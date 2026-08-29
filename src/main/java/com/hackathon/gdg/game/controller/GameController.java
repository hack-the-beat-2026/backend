package com.hackathon.gdg.game.controller;

import com.hackathon.gdg.game.dto.GameResponse;
import com.hackathon.gdg.game.service.GameService;
import com.hackathon.gdg.global.security.AuthenticatedActor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GameController {

	private final GameService gameService;

	public GameController(GameService gameService) {
		this.gameService = gameService;
	}

	@PostMapping("/rooms/{roomId}/games/start")
	public GameResponse startGame(
			@PathVariable Long roomId,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return gameService.startGame(roomId, actor);
	}

	@GetMapping("/games/{gameId}")
	public GameResponse getGame(
			@PathVariable Long gameId,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return gameService.getGame(gameId, actor);
	}
}
