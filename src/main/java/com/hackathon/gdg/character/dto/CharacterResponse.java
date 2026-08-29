package com.hackathon.gdg.character.dto;

import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.domain.CharacterStatus;

import java.time.Instant;

public record CharacterResponse(
		Long characterId,
		Long gameId,
		Long participantId,
		String nickname,
		String templateType,
		String originalPhotoUrl,
		String characterImageUrl,
		String previewImageUrl,
		Double positionX,
		Double positionY,
		Double scale,
		Double rotation,
		String qrToken,
		CharacterStatus status,
		Instant submittedAt
) {
	public static CharacterResponse from(Character character) {
		return new CharacterResponse(
				character.getId(),
				character.getGame().getId(),
				character.getParticipant().getId(),
				character.getParticipant().getNickname(),
				character.getTemplateType(),
				character.getOriginalPhotoUrl(),
				character.getCharacterImageUrl(),
				character.getPreviewImageUrl(),
				character.getPositionX(),
				character.getPositionY(),
				character.getScale(),
				character.getRotation(),
				character.getQrToken(),
				character.getStatus(),
				character.getSubmittedAt()
		);
	}
}
