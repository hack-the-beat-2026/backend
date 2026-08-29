package com.hackathon.gdg.game.dto;

import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.domain.Winner;
import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.domain.ParticipantStatus;

import java.time.Instant;

public record GameResponse(
		Long gameId,
		Long roomId,
		GameStatus status,
		GameRole myRole,
		ParticipantStatus myParticipantStatus,
		int seekerCount,
		long hiderCount,
		int designDurationSeconds,
		int hideDurationSeconds,
		int seekDurationSeconds,
		Instant designStartedAt,
		Instant designEndsAt,
		Instant hideStartedAt,
		Instant hideEndsAt,
		Instant seekStartedAt,
		Instant seekEndsAt,
		Instant finishedAt,
		Winner winner
) {
}
