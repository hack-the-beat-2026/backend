package com.hackathon.gdg.result.dto;

import com.hackathon.gdg.character.domain.CharacterStatus;
import com.hackathon.gdg.participant.domain.ParticipantStatus;

import java.time.Instant;

public record HiderResultResponse(
		Long participantId,
		String nickname,
		Long characterId,
		ParticipantStatus participantStatus,
		CharacterStatus characterStatus,
		long survivalSeconds,
		Instant foundAt,
		Long foundByParticipantId,
		String foundByNickname,
		String previewImageUrl
) {
}
