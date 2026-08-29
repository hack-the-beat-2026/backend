package com.hackathon.gdg.scan.service;

import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.domain.CharacterStatus;
import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.game.domain.Game;
import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.domain.Winner;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.game.service.GameCompletionService;
import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.domain.Participant;
import com.hackathon.gdg.participant.domain.ParticipantStatus;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.scan.dto.FoundCharacterResponse;
import com.hackathon.gdg.scan.dto.QrCharacterResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class ScanService {

	private final GameRepository gameRepository;
	private final CharacterRepository characterRepository;
	private final ParticipantRepository participantRepository;
	private final GameCompletionService completionService;
	private final Clock clock;

	public ScanService(
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

	@Transactional(noRollbackFor = SeekTimeExpiredException.class)
	public QrCharacterResponse lookup(String qrToken, AuthenticatedActor actor) {
		Long gameId = characterRepository.findGameIdByQrToken(qrToken)
				.orElseThrow(() -> invalidQrToken());
		Game game = gameRepository.findByIdForUpdate(gameId)
				.orElseThrow(() -> invalidQrToken());
		requireSeeker(game, actor);
		validateSeeking(game);
		Character character = characterRepository.findByQrTokenForUpdate(qrToken)
				.orElseThrow(() -> invalidQrToken());
		return new QrCharacterResponse(game.getId(), character.getId(), character.getStatus());
	}

	@Transactional(noRollbackFor = SeekTimeExpiredException.class)
	public FoundCharacterResponse markFound(
			Long gameId,
			String qrToken,
			AuthenticatedActor actor
	) {
		Game game = gameRepository.findByIdForUpdate(gameId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.GAME_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"게임을 찾을 수 없습니다."
				));
		Participant seeker = requireSeeker(game, actor);
		validateSeeking(game);

		Character character = characterRepository.findByQrTokenForUpdate(qrToken)
				.orElseThrow(() -> invalidQrToken());
		if (!character.getGame().getId().equals(gameId)) {
			throw invalidQrToken();
		}
		if (character.getStatus() == CharacterStatus.FOUND) {
			throw new ApiException(
					ErrorCode.CHARACTER_ALREADY_FOUND,
					HttpStatus.CONFLICT,
					"이미 발견된 Character입니다."
			);
		}
		if (character.getStatus() != CharacterStatus.HIDDEN) {
			throw new ApiException(
					ErrorCode.GAME_INVALID_STATE,
					HttpStatus.CONFLICT,
					"현재 상태의 Character는 발견 처리할 수 없습니다."
			);
		}

		Instant foundAt = clock.instant();
		character.markFound(seeker, foundAt);
		character.getParticipant().eliminate();
		completionService.finishIfAllHidersFound(game, foundAt);

		long survivalSeconds = Math.max(0, Duration.between(game.getSeekStartedAt(), foundAt).getSeconds());
		return new FoundCharacterResponse(
				character.getId(),
				character.getParticipant().getNickname(),
				character.getOriginalPhotoUrl(),
				character.getPreviewImageUrl(),
				survivalSeconds,
				game.getStatus() == GameStatus.FINISHED,
				game.getStatus() == GameStatus.FINISHED ? game.getWinner() : Winner.NONE
		);
	}

	private Participant requireSeeker(Game game, AuthenticatedActor actor) {
		if (actor == null || actor.isHost() || !game.getRoom().getId().equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "해당 게임의 SEEKER만 QR을 처리할 수 있습니다.");
		}
		Participant participant = participantRepository.findById(actor.participantId())
				.orElseThrow(() -> new ApiException(
						ErrorCode.ACCESS_DENIED,
						HttpStatus.FORBIDDEN,
						"참가자 정보를 찾을 수 없습니다."
				));
		if (participant.getGameRole() != GameRole.SEEKER || participant.getStatus() != ParticipantStatus.ACTIVE) {
			throw new ApiException(
					ErrorCode.INVALID_GAME_ROLE,
					HttpStatus.FORBIDDEN,
					"ACTIVE 상태의 SEEKER만 QR을 처리할 수 있습니다."
			);
		}
		return participant;
	}

	private void validateSeeking(Game game) {
		if (game.getStatus() != GameStatus.SEEKING) {
			throw new ApiException(
					ErrorCode.GAME_INVALID_STATE,
					HttpStatus.CONFLICT,
					"SEEKING 상태에서만 QR을 처리할 수 있습니다."
			);
		}
		if (completionService.finishIfSeekTimeExpired(game, clock.instant())) {
			throw new SeekTimeExpiredException();
		}
	}

	private ApiException invalidQrToken() {
		return new ApiException(ErrorCode.INVALID_QR_TOKEN, HttpStatus.NOT_FOUND, "유효하지 않은 QR Token입니다.");
	}
}
