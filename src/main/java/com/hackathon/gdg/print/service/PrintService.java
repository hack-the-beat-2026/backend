package com.hackathon.gdg.print.service;

import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.game.domain.Game;
import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.print.dto.PrintCharacterResponse;
import com.hackathon.gdg.print.dto.PrintSheetResponse;
import com.hackathon.gdg.qr.service.QrService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PrintService {

	private final GameRepository gameRepository;
	private final CharacterRepository characterRepository;
	private final QrService qrService;

	public PrintService(
			GameRepository gameRepository,
			CharacterRepository characterRepository,
			QrService qrService
	) {
		this.gameRepository = gameRepository;
		this.characterRepository = characterRepository;
		this.qrService = qrService;
	}

	@Transactional(readOnly = true)
	public PrintSheetResponse getPrintSheet(Long gameId, AuthenticatedActor actor) {
		Game game = requirePrintableGame(gameId, actor);
		List<Character> characters = characterRepository.findAllByGameIdOrderByIdAsc(gameId);
		if (characters.isEmpty()) {
			throw new ApiException(
					ErrorCode.GAME_INVALID_STATE,
					HttpStatus.CONFLICT,
					"인쇄할 Character가 없습니다."
			);
		}

		List<PrintCharacterResponse> slots = new ArrayList<>(characters.size());
		for (int index = 0; index < characters.size(); index++) {
			Character character = characters.get(index);
			slots.add(new PrintCharacterResponse(
					index + 1,
					character.getId(),
					character.getCharacterImageUrl(),
					qrImageUrl(gameId, character.getId()),
					character.getQrToken()
			));
		}
		return new PrintSheetResponse(game.getId(), "A4", "PORTRAIT", "LONG_EDGE", 100, 3, slots);
	}

	@Transactional(readOnly = true)
	public byte[] getQrImage(Long gameId, Long characterId, AuthenticatedActor actor) {
		requirePrintableGame(gameId, actor);
		Character character = characterRepository.findById(characterId)
				.orElseThrow(() -> characterNotFound());
		if (!character.getGame().getId().equals(gameId)) {
			throw characterNotFound();
		}
		return qrService.generatePng(character.getQrToken());
	}

	private Game requirePrintableGame(Long gameId, AuthenticatedActor actor) {
		Game game = gameRepository.findById(gameId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.GAME_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"게임을 찾을 수 없습니다."
				));
		if (actor == null || !actor.isHost() || !game.getRoom().getId().equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "해당 게임의 HOST만 인쇄할 수 있습니다.");
		}
		if (game.getStatus() != GameStatus.PRINTING) {
			throw new ApiException(
					ErrorCode.GAME_INVALID_STATE,
					HttpStatus.CONFLICT,
					"PRINTING 상태에서만 인쇄 데이터를 조회할 수 있습니다."
			);
		}
		return game;
	}

	private String qrImageUrl(Long gameId, Long characterId) {
		return "/api/v1/games/" + gameId + "/characters/" + characterId + "/qr";
	}

	private ApiException characterNotFound() {
		return new ApiException(ErrorCode.CHARACTER_NOT_FOUND, HttpStatus.NOT_FOUND, "Character를 찾을 수 없습니다.");
	}
}
