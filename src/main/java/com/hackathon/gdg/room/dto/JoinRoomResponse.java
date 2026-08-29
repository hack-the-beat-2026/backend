package com.hackathon.gdg.room.dto;

public record JoinRoomResponse(
		Long participantId,
		String participantToken,
		Long roomId,
		Long gameId
) {
}
