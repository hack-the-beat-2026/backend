package com.hackathon.gdg.room.dto;

import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.domain.ParticipantStatus;
import com.hackathon.gdg.participant.domain.ParticipantType;

import java.time.Instant;

public record ParticipantResponse(
		Long participantId,
		String nickname,
		ParticipantType type,
		GameRole gameRole,
		ParticipantStatus status,
		Instant joinedAt
) {
}
