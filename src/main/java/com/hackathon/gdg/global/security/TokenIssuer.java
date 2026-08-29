package com.hackathon.gdg.global.security;

import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.room.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TokenIssuer {

	private static final int MAX_ATTEMPTS = 10;

	private final TokenService tokenService;
	private final RoomRepository roomRepository;
	private final ParticipantRepository participantRepository;

	public TokenIssuer(
			TokenService tokenService,
			RoomRepository roomRepository,
			ParticipantRepository participantRepository
	) {
		this.tokenService = tokenService;
		this.roomRepository = roomRepository;
		this.participantRepository = participantRepository;
	}

	public IssuedToken issue() {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String rawToken = tokenService.generate();
			String tokenHash = tokenService.hash(rawToken);
			if (!roomRepository.existsByHostTokenHash(tokenHash)
					&& !participantRepository.existsByParticipantTokenHash(tokenHash)) {
				return new IssuedToken(rawToken, tokenHash);
			}
		}

		throw new ApiException(
				ErrorCode.TOKEN_GENERATION_FAILED,
				HttpStatus.INTERNAL_SERVER_ERROR,
				"고유 Token을 생성하지 못했습니다."
		);
	}
}
