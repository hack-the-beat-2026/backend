package com.hackathon.gdg.result.controller;

import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.result.dto.GameResultResponse;
import com.hackathon.gdg.result.service.ResultService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/games/{gameId}/result")
public class ResultController {

	private final ResultService resultService;

	public ResultController(ResultService resultService) {
		this.resultService = resultService;
	}

	@GetMapping
	public GameResultResponse getResult(
			@PathVariable Long gameId,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return resultService.getResult(gameId, actor);
	}
}
