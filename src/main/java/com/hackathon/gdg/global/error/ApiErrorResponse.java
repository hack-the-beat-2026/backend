package com.hackathon.gdg.global.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
		String code,
		String message,
		Instant timestamp,
		Map<String, String> fieldErrors
) {
	public static ApiErrorResponse of(ErrorCode code, String message) {
		return new ApiErrorResponse(code.name(), message, Instant.now(), Map.of());
	}

	public static ApiErrorResponse validation(Map<String, String> fieldErrors) {
		return new ApiErrorResponse(
				ErrorCode.INVALID_REQUEST.name(),
				"요청 값을 확인해 주세요.",
				Instant.now(),
				fieldErrors
		);
	}
}
