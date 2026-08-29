package com.hackathon.gdg.scan.dto;

import com.hackathon.gdg.character.domain.CharacterStatus;

public record QrCharacterResponse(
		Long gameId,
		Long characterId,
		CharacterStatus status
) {
}
