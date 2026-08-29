package com.hackathon.gdg.game.service;

import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.domain.CharacterStatus;
import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.game.domain.Game;
import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.dto.GameResponse;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.domain.Participant;
import com.hackathon.gdg.participant.domain.ParticipantStatus;
import com.hackathon.gdg.participant.domain.ParticipantType;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.room.domain.Room;
import com.hackathon.gdg.room.domain.RoomStatus;
import com.hackathon.gdg.room.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class GameService {

	private final RoomRepository roomRepository;
	private final GameRepository gameRepository;
	private final ParticipantRepository participantRepository;
	private final CharacterRepository characterRepository;
	private final RandomRoleAssigner roleAssigner;
	private final GameCompletionService completionService;
	private final Clock clock;

	public GameService(
			RoomRepository roomRepository,
			GameRepository gameRepository,
			ParticipantRepository participantRepository,
			CharacterRepository characterRepository,
			RandomRoleAssigner roleAssigner,
			GameCompletionService completionService,
			Clock clock
	) {
		this.roomRepository = roomRepository;
		this.gameRepository = gameRepository;
		this.participantRepository = participantRepository;
		this.characterRepository = characterRepository;
		this.roleAssigner = roleAssigner;
		this.completionService = completionService;
		this.clock = clock;
	}

	@Transactional
	public GameResponse startGame(Long roomId, AuthenticatedActor actor) {
		validateHost(actor, roomId);
		Room room = roomRepository.findByIdForUpdate(roomId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.ROOM_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"방을 찾을 수 없습니다."
				));
		Game game = findCurrentGame(roomId);

		if (room.getStatus() != RoomStatus.WAITING || game.getStatus() != GameStatus.WAITING) {
			throw new ApiException(
					ErrorCode.GAME_INVALID_STATE,
					HttpStatus.CONFLICT,
					"이미 시작했거나 현재 시작할 수 없는 게임입니다."
			);
		}

		List<Participant> players = participantRepository
				.findAllByRoomIdAndTypeAndStatusOrderByJoinedAtAsc(
						roomId,
						ParticipantType.PLAYER,
						ParticipantStatus.WAITING
				);
		if (players.size() < 2) {
			throw new ApiException(
					ErrorCode.INSUFFICIENT_PARTICIPANTS,
					HttpStatus.CONFLICT,
					"게임을 시작하려면 PLAYER가 최소 2명 필요합니다."
			);
		}
		if (game.getSeekerCount() >= players.size()) {
			throw new ApiException(
					ErrorCode.INVALID_SEEKER_COUNT,
					HttpStatus.CONFLICT,
					"SEEKER 수는 전체 PLAYER 수보다 작아야 합니다."
			);
		}

		roleAssigner.assign(players, game.getSeekerCount());
		Instant startedAt = clock.instant();
		room.startGame();
		game.startDesigning(startedAt);

		return toResponse(game, GameRole.NONE, null);
	}

	@Transactional
	public GameResponse getGame(Long gameId, AuthenticatedActor actor) {
		Game game = gameRepository.findByIdForUpdate(gameId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.GAME_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"게임을 찾을 수 없습니다."
				));
		Long roomId = game.getRoom().getId();
		if (actor == null || !roomId.equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "해당 게임에 접근할 수 없습니다.");
		}
		completionService.finishIfSeekTimeExpired(game, clock.instant());

		if (actor.isHost()) {
			return toResponse(game, GameRole.NONE, null);
		}
		Participant participant = participantRepository.findById(actor.participantId())
				.orElseThrow(() -> new ApiException(
						ErrorCode.ACCESS_DENIED,
						HttpStatus.FORBIDDEN,
						"참가자 정보를 찾을 수 없습니다."
				));
		return toResponse(game, participant.getGameRole(), participant.getStatus());
	}

	@Transactional
	public GameResponse startHiding(Long gameId, AuthenticatedActor actor) {
		Game game = findGameForUpdate(gameId);
		validateHost(actor, game.getRoom().getId(), "해당 방의 HOST만 숨기기를 시작할 수 있습니다.");
		if (game.getStatus() != GameStatus.PRINTING) {
			throw invalidState("PRINTING 상태에서만 숨기기를 시작할 수 있습니다.");
		}
		List<Character> characters = characterRepository.findAllByGameIdOrderByIdAsc(gameId);
		if (characters.isEmpty() || characters.stream().anyMatch(character -> character.getStatus() != CharacterStatus.SUBMITTED)) {
			throw invalidState("모든 Character가 제출된 상태여야 합니다.");
		}
		Instant startedAt = clock.instant();
		characters.forEach(character -> character.markPrinted(startedAt));
		game.startHiding(startedAt);
		return toResponse(game, GameRole.NONE, null);
	}

	@Transactional
	public GameResponse startSeeking(Long gameId, AuthenticatedActor actor) {
		Game game = findGameForUpdate(gameId);
		validateHost(actor, game.getRoom().getId(), "해당 방의 HOST만 탐색을 시작할 수 있습니다.");
		if (game.getStatus() != GameStatus.HIDING) {
			throw invalidState("HIDING 상태에서만 탐색을 시작할 수 있습니다.");
		}
		Instant now = clock.instant();
		if (now.isBefore(game.getHideEndsAt())) {
			throw new ApiException(
					ErrorCode.HIDE_TIME_NOT_EXPIRED,
					HttpStatus.CONFLICT,
					"숨기기 제한시간이 아직 끝나지 않았습니다."
			);
		}
		List<Character> characters = characterRepository.findAllByGameIdOrderByIdAsc(gameId);
		if (characters.isEmpty() || characters.stream().anyMatch(character -> character.getStatus() != CharacterStatus.HIDDEN)) {
			throw new ApiException(
					ErrorCode.HIDERS_NOT_READY,
					HttpStatus.CONFLICT,
					"모든 HIDER가 숨기기 완료 상태여야 합니다."
			);
		}
		game.startSeeking(now);
		return toResponse(game, GameRole.NONE, null);
	}

	@Transactional
	public GameResponse finishGame(Long gameId, AuthenticatedActor actor) {
		Game game = findGameForUpdate(gameId);
		validateHost(actor, game.getRoom().getId(), "해당 방의 HOST만 게임 종료를 확인할 수 있습니다.");
		if (game.getStatus() == GameStatus.FINISHED) {
			return toResponse(game, GameRole.NONE, null);
		}
		if (game.getStatus() != GameStatus.SEEKING) {
			throw invalidState("SEEKING 상태의 게임만 종료할 수 있습니다.");
		}
		Instant now = clock.instant();
		if (!completionService.finishIfAllHidersFound(game, now)
				&& !completionService.finishIfSeekTimeExpired(game, now)) {
			throw new ApiException(
					ErrorCode.SEEK_TIME_NOT_EXPIRED,
					HttpStatus.CONFLICT,
					"탐색 제한시간이 아직 끝나지 않았습니다."
			);
		}
		return toResponse(game, GameRole.NONE, null);
	}

	private GameResponse toResponse(Game game, GameRole myRole, ParticipantStatus participantStatus) {
		Long roomId = game.getRoom().getId();
		return new GameResponse(
				game.getId(),
				roomId,
				game.getStatus(),
				myRole,
				participantStatus,
				game.getSeekerCount(),
				participantRepository.countByRoomIdAndGameRole(roomId, GameRole.HIDER),
				game.getDesignDurationSeconds(),
				game.getHideDurationSeconds(),
				game.getSeekDurationSeconds(),
				game.getDesignStartedAt(),
				game.getDesignEndsAt(),
				game.getHideStartedAt(),
				game.getHideEndsAt(),
				game.getSeekStartedAt(),
				game.getSeekEndsAt(),
				game.getFinishedAt(),
				game.getWinner()
		);
	}

	private Game findGameForUpdate(Long gameId) {
		return gameRepository.findByIdForUpdate(gameId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.GAME_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"게임을 찾을 수 없습니다."
				));
	}

	private ApiException invalidState(String message) {
		return new ApiException(ErrorCode.GAME_INVALID_STATE, HttpStatus.CONFLICT, message);
	}

	private Game findCurrentGame(Long roomId) {
		return gameRepository.findFirstByRoomIdOrderByCreatedAtDesc(roomId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.GAME_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"게임을 찾을 수 없습니다."
				));
	}

	private void validateHost(AuthenticatedActor actor, Long roomId) {
		validateHost(actor, roomId, "해당 방의 HOST만 게임을 시작할 수 있습니다.");
	}

	private void validateHost(AuthenticatedActor actor, Long roomId, String message) {
		if (actor == null || !actor.isHost() || !roomId.equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, message);
		}
	}
}
