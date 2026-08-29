package com.hackathon.gdg.game.service;

import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.domain.CharacterStatus;
import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.game.domain.Game;
import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.domain.Winner;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class GameCompletionService {

	private final CharacterRepository characterRepository;

	public GameCompletionService(CharacterRepository characterRepository) {
		this.characterRepository = characterRepository;
	}

	public boolean finishIfSeekTimeExpired(Game game, Instant now) {
		if (game.getStatus() != GameStatus.SEEKING || now.isBefore(game.getSeekEndsAt())) {
			return false;
		}
		List<Character> characters = characterRepository.findAllByGameIdOrderByIdAsc(game.getId());
		boolean hasSurvivor = characters.stream().anyMatch(character -> character.getStatus() == CharacterStatus.HIDDEN);
		if (hasSurvivor) {
			characters.stream()
					.filter(character -> character.getStatus() == CharacterStatus.HIDDEN)
					.forEach(character -> {
						character.markSurvived();
						character.getParticipant().survive();
					});
			finish(game, Winner.HIDER, now);
		} else {
			finish(game, Winner.SEEKER, now);
		}
		return true;
	}

	public boolean finishIfAllHidersFound(Game game, Instant now) {
		if (game.getStatus() != GameStatus.SEEKING) {
			return false;
		}
		List<Character> characters = characterRepository.findAllByGameIdOrderByIdAsc(game.getId());
		if (characters.isEmpty() || characters.stream().anyMatch(character -> character.getStatus() != CharacterStatus.FOUND)) {
			return false;
		}
		finish(game, Winner.SEEKER, now);
		return true;
	}

	private void finish(Game game, Winner winner, Instant now) {
		game.finish(winner, now);
		game.getRoom().finishGame();
	}
}
