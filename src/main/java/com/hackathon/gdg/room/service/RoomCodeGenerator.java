package com.hackathon.gdg.room.service;

import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import com.hackathon.gdg.room.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class RoomCodeGenerator {

	private static final char[] CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final int CODE_LENGTH = 6;
	private static final int MAX_ATTEMPTS = 20;

	private final SecureRandom secureRandom = new SecureRandom();
	private final RoomRepository roomRepository;

	public RoomCodeGenerator(RoomRepository roomRepository) {
		this.roomRepository = roomRepository;
	}

	public String generateUnique() {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String code = generate();
			if (!roomRepository.existsByRoomCode(code)) {
				return code;
			}
		}
		throw new ApiException(
				ErrorCode.ROOM_CODE_GENERATION_FAILED,
				HttpStatus.INTERNAL_SERVER_ERROR,
				"고유 방 코드를 생성하지 못했습니다."
		);
	}

	private String generate() {
		StringBuilder builder = new StringBuilder(CODE_LENGTH);
		for (int index = 0; index < CODE_LENGTH; index++) {
			builder.append(CHARACTERS[secureRandom.nextInt(CHARACTERS.length)]);
		}
		return builder.toString();
	}
}
