package com.hackathon.gdg.result.service;

import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.domain.CharacterStatus;
import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.game.domain.Game;
import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.game.service.GameCompletionService;
import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.domain.Participant;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.result.dto.GameResultResponse;
import com.hackathon.gdg.result.dto.HiderResultResponse;
import com.hackathon.gdg.result.dto.SeekerResultResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Service
public class ResultService {

	private final GameRepository gameRepository;
	private final CharacterRepository characterRepository;
	private final ParticipantRepository participantRepository;
	private final GameCompletionService completionService;
	private final Clock clock;

	public ResultService(
			GameRepository gameRepository,
			CharacterRepository characterRepository,
			ParticipantRepository participantRepository,
			GameCompletionService completionService,
			Clock clock
	) {
		this.gameRepository = gameRepository;
		this.characterRepository = characterRepository;
		this.participantRepository = participantRepository;
		this.completionService = completionService;
		this.clock = clock;
	}

	@Transactional
	public GameResultResponse getResult(Long gameId, AuthenticatedActor actor) {
		Game game = gameRepository.findByIdForUpdate(gameId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.GAME_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"게임을 찾을 수 없습니다."
				));
		if (actor == null || !game.getRoom().getId().equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "해당 게임 결과에 접근할 수 없습니다.");
		}
		completionService.finishIfSeekTimeExpired(game, clock.instant());
		if (game.getStatus() != GameStatus.FINISHED) {
			throw new ApiException(
					ErrorCode.GAME_INVALID_STATE,
					HttpStatus.CONFLICT,
					"FINISHED 상태에서만 결과를 조회할 수 있습니다."
			);
		}

		List<Character> characters = characterRepository.findAllByGameIdOrderByIdAsc(gameId);
		List<HiderResultResponse> hiders = characters.stream()
				.map(character -> toHiderResult(game, character))
				.sorted(Comparator.comparingLong(HiderResultResponse::survivalSeconds).reversed()
						.thenComparing(HiderResultResponse::participantId))
				.toList();

		List<Participant> participants = participantRepository.findAllByRoomIdOrderByJoinedAtAsc(game.getRoom().getId());
		List<SeekerResultResponse> seekers = participants.stream()
				.filter(participant -> participant.getGameRole() == GameRole.SEEKER)
				.map(seeker -> new SeekerResultResponse(
						seeker.getId(),
						seeker.getNickname(),
						characters.stream()
								.filter(character -> character.getFoundByParticipant() != null)
								.filter(character -> character.getFoundByParticipant().getId().equals(seeker.getId()))
								.count()
				))
				.sorted(Comparator.comparingLong(SeekerResultResponse::foundCount).reversed()
						.thenComparing(SeekerResultResponse::participantId))
				.toList();

		return new GameResultResponse(
				game.getId(),
				game.getStatus(),
				game.getWinner(),
				game.getSeekStartedAt(),
				game.getSeekEndsAt(),
				game.getFinishedAt(),
				hiders,
				seekers
		);
	}

	private HiderResultResponse toHiderResult(Game game, Character character) {
		long survivalSeconds = character.getStatus() == CharacterStatus.FOUND
				? Math.max(0, Duration.between(game.getSeekStartedAt(), character.getFoundAt()).getSeconds())
				: game.getSeekDurationSeconds();
		Participant foundBy = character.getFoundByParticipant();
		return new HiderResultResponse(
				character.getParticipant().getId(),
				character.getParticipant().getNickname(),
				character.getId(),
				character.getParticipant().getStatus(),
				character.getStatus(),
				survivalSeconds,
				character.getFoundAt(),
				foundBy == null ? null : foundBy.getId(),
				foundBy == null ? null : foundBy.getNickname(),
				character.getPreviewImageUrl()
		);
	}
}
