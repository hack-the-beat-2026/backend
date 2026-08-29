package com.hackathon.gdg.room.dto;

import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.room.domain.RoomStatus;

public record RoomResponse(
		Long roomId,
		Long gameId,
		String roomCode,
		String name,
		RoomStatus roomStatus,
		GameStatus gameStatus,
		int designDurationSeconds,
		int hideDurationSeconds,
		int seekDurationSeconds,
		int seekerCount
) {
}
