package com.hackathon.gdg.result.dto;

import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.domain.Winner;

import java.time.Instant;
import java.util.List;

public record GameResultResponse(
		Long gameId,
		GameStatus status,
		Winner winner,
		Instant seekStartedAt,
		Instant seekEndsAt,
		Instant finishedAt,
		List<HiderResultResponse> hiders,
		List<SeekerResultResponse> seekers
) {
}
