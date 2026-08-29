package com.hackathon.gdg.result.dto;

public record SeekerResultResponse(
		Long participantId,
		String nickname,
		long foundCount
) {
}
