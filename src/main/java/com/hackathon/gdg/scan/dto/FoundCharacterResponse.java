package com.hackathon.gdg.scan.dto;

import com.hackathon.gdg.game.domain.Winner;

public record FoundCharacterResponse(
		Long characterId,
		String hiderNickname,
		String originalPhotoUrl,
		String previewImageUrl,
		long survivalSeconds,
		boolean gameFinished,
		Winner winner
) {
}
