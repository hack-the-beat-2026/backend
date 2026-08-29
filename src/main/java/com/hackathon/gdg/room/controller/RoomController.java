package com.hackathon.gdg.room.controller;

import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.room.dto.CreateRoomRequest;
import com.hackathon.gdg.room.dto.CreateRoomResponse;
import com.hackathon.gdg.room.dto.JoinRoomRequest;
import com.hackathon.gdg.room.dto.JoinRoomResponse;
import com.hackathon.gdg.room.dto.ParticipantResponse;
import com.hackathon.gdg.room.dto.RoomResponse;
import com.hackathon.gdg.room.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

	private final RoomService roomService;

	public RoomController(RoomService roomService) {
		this.roomService = roomService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateRoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
		return roomService.createRoom(request);
	}

	@GetMapping("/{roomCode}")
	public RoomResponse getRoom(@PathVariable String roomCode) {
		return roomService.getRoom(roomCode);
	}

	@PostMapping("/{roomCode}/participants")
	@ResponseStatus(HttpStatus.CREATED)
	public JoinRoomResponse joinRoom(
			@PathVariable String roomCode,
			@Valid @RequestBody JoinRoomRequest request
	) {
		return roomService.joinRoom(roomCode, request);
	}

	@GetMapping("/{roomId}/participants")
	public List<ParticipantResponse> getParticipants(
			@PathVariable Long roomId,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return roomService.getParticipants(roomId, actor);
	}
}
