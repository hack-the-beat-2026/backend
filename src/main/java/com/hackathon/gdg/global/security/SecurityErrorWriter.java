package com.hackathon.gdg.global.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;

final class SecurityErrorWriter {

	private SecurityErrorWriter() {
	}

	static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("""
				{"code":"%s","message":"%s","timestamp":"%s","fieldErrors":{}}
				""".formatted(code, message, Instant.now()));
	}
}
