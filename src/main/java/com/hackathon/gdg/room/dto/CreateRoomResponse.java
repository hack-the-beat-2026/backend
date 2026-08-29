package com.hackathon.gdg.room.dto;

public record CreateRoomResponse(
		Long roomId,
		Long gameId,
		String roomCode,
		String hostToken,
		String joinUrl
) {
}
