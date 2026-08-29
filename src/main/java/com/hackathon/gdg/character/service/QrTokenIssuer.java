package com.hackathon.gdg.character.service;

import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import com.hackathon.gdg.global.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class QrTokenIssuer {

	private static final int MAX_ATTEMPTS = 10;

	private final TokenService tokenService;
	private final CharacterRepository characterRepository;

	public QrTokenIssuer(TokenService tokenService, CharacterRepository characterRepository) {
		this.tokenService = tokenService;
		this.characterRepository = characterRepository;
	}

	public String issue() {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String token = tokenService.generate();
			if (!characterRepository.existsByQrToken(token)) {
				return token;
			}
		}
		throw new ApiException(
				ErrorCode.TOKEN_GENERATION_FAILED,
				HttpStatus.INTERNAL_SERVER_ERROR,
				"고유 QR Token을 생성하지 못했습니다."
		);
	}
}
