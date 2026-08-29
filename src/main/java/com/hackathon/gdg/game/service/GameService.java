package com.hackathon.gdg.game.service;

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
	private final RandomRoleAssigner roleAssigner;
	private final Clock clock;

	public GameService(
			RoomRepository roomRepository,
			GameRepository gameRepository,
			ParticipantRepository participantRepository,
			RandomRoleAssigner roleAssigner,
			Clock clock
	) {
		this.roomRepository = roomRepository;
		this.gameRepository = gameRepository;
		this.participantRepository = participantRepository;
		this.roleAssigner = roleAssigner;
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

	@Transactional(readOnly = true)
	public GameResponse getGame(Long gameId, AuthenticatedActor actor) {
		Game game = gameRepository.findById(gameId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.GAME_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"게임을 찾을 수 없습니다."
				));
		Long roomId = game.getRoom().getId();
		if (actor == null || !roomId.equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "해당 게임에 접근할 수 없습니다.");
		}

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
				game.getSeekStartedAt(),
				game.getFinishedAt(),
				game.getWinner()
		);
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
		if (actor == null || !actor.isHost() || !roomId.equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "해당 방의 HOST만 게임을 시작할 수 있습니다.");
		}
	}
}
