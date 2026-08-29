package com.hackathon.gdg.character.service;

import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.dto.CharacterResponse;
import com.hackathon.gdg.character.dto.CharacterSubmitRequest;
import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.game.domain.Game;
import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.global.storage.LocalImageStorage;
import com.hackathon.gdg.global.storage.StoredImages;
import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.domain.Participant;
import com.hackathon.gdg.participant.domain.ParticipantStatus;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class CharacterService {

	private static final long SUBMISSION_GRACE_SECONDS = 5;

	private final GameRepository gameRepository;
	private final ParticipantRepository participantRepository;
	private final CharacterRepository characterRepository;
	private final LocalImageStorage imageStorage;
	private final QrTokenIssuer qrTokenIssuer;
	private final Clock clock;

	public CharacterService(
			GameRepository gameRepository,
			ParticipantRepository participantRepository,
			CharacterRepository characterRepository,
			LocalImageStorage imageStorage,
			QrTokenIssuer qrTokenIssuer,
			Clock clock
	) {
		this.gameRepository = gameRepository;
		this.participantRepository = participantRepository;
		this.characterRepository = characterRepository;
		this.imageStorage = imageStorage;
		this.qrTokenIssuer = qrTokenIssuer;
		this.clock = clock;
	}

	@Transactional
	public CharacterResponse submit(
			Long gameId,
			AuthenticatedActor actor,
			CharacterSubmitRequest request,
			MultipartFile originalPhoto,
			MultipartFile characterImage,
			MultipartFile previewImage
	) {
		Game game = findGameForUpdate(gameId);
		Participant participant = requireHider(game, actor);
		validateSubmissionWindow(game);
		if (characterRepository.existsByGameIdAndParticipantId(gameId, participant.getId())) {
			throw new ApiException(
					ErrorCode.CHARACTER_ALREADY_SUBMITTED,
					HttpStatus.CONFLICT,
					"이미 Character를 제출했습니다."
			);
		}

		StoredImages images = null;
		try {
			images = imageStorage.store(gameId, participant.getId(), originalPhoto, characterImage, previewImage);
			Character character = Character.submit(
					game,
					participant,
					request.templateType().trim(),
					images.originalPhotoUrl(),
					images.characterImageUrl(),
					images.previewImageUrl(),
					request.positionX(),
					request.positionY(),
					request.scale(),
					request.rotation(),
					qrTokenIssuer.issue()
			);
			characterRepository.saveAndFlush(character);

			long submittedCount = characterRepository.countByGameId(gameId);
			long hiderCount = participantRepository.countByRoomIdAndGameRole(game.getRoom().getId(), GameRole.HIDER);
			if (hiderCount > 0 && submittedCount == hiderCount) {
				game.completeDesigning();
			}
			return CharacterResponse.from(character);
		} catch (RuntimeException exception) {
			imageStorage.delete(images);
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public CharacterResponse getMine(Long gameId, AuthenticatedActor actor) {
		Game game = findGame(gameId);
		Participant participant = requireHider(game, actor);
		Character character = characterRepository.findByGameIdAndParticipantId(gameId, participant.getId())
				.orElseThrow(() -> new ApiException(
						ErrorCode.CHARACTER_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"제출한 Character가 없습니다."
				));
		return CharacterResponse.from(character);
	}

	@Transactional(readOnly = true)
	public List<CharacterResponse> getAll(Long gameId, AuthenticatedActor actor) {
		Game game = findGame(gameId);
		if (actor == null || !actor.isHost() || !game.getRoom().getId().equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "해당 게임의 HOST만 조회할 수 있습니다.");
		}
		return characterRepository.findAllByGameIdOrderByIdAsc(gameId).stream()
				.map(CharacterResponse::from)
				.toList();
	}

	private Game findGameForUpdate(Long gameId) {
		return gameRepository.findByIdForUpdate(gameId)
				.orElseThrow(() -> gameNotFound());
	}

	private Game findGame(Long gameId) {
		return gameRepository.findById(gameId)
				.orElseThrow(() -> gameNotFound());
	}

	private ApiException gameNotFound() {
		return new ApiException(ErrorCode.GAME_NOT_FOUND, HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다.");
	}

	private Participant requireHider(Game game, AuthenticatedActor actor) {
		if (actor == null || actor.isHost() || !game.getRoom().getId().equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "해당 게임에 접근할 수 없습니다.");
		}
		Participant participant = participantRepository.findById(actor.participantId())
				.orElseThrow(() -> new ApiException(
						ErrorCode.ACCESS_DENIED,
						HttpStatus.FORBIDDEN,
						"참가자 정보를 찾을 수 없습니다."
				));
		if (participant.getGameRole() != GameRole.HIDER || participant.getStatus() != ParticipantStatus.ACTIVE) {
			throw new ApiException(
					ErrorCode.INVALID_GAME_ROLE,
					HttpStatus.FORBIDDEN,
					"ACTIVE 상태의 HIDER만 Character를 제출하거나 조회할 수 있습니다."
			);
		}
		return participant;
	}

	private void validateSubmissionWindow(Game game) {
		if (game.getStatus() != GameStatus.DESIGNING) {
			throw new ApiException(
					ErrorCode.GAME_INVALID_STATE,
					HttpStatus.CONFLICT,
					"DESIGNING 상태에서만 Character를 제출할 수 있습니다."
			);
		}
		Instant deadline = game.getDesignEndsAt().plusSeconds(SUBMISSION_GRACE_SECONDS);
		if (clock.instant().isAfter(deadline)) {
			throw new ApiException(
					ErrorCode.DESIGN_TIME_EXPIRED,
					HttpStatus.CONFLICT,
					"Character 제출 가능 시간이 지났습니다."
			);
		}
	}
}
