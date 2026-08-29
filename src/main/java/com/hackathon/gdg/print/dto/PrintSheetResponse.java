package com.hackathon.gdg.print.dto;

import java.util.List;

public record PrintSheetResponse(
		Long gameId,
		String paperSize,
		String orientation,
		String duplexFlip,
		int scalePercent,
		int columns,
		List<PrintCharacterResponse> characters
) {
}
