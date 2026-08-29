package com.hackathon.gdg.print.dto;

public record PrintCharacterResponse(
		int printSlot,
		Long characterId,
		String characterImageUrl,
		String qrImageUrl,
		String qrToken
) {
}
