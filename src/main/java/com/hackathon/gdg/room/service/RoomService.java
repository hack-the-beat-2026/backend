package com.hackathon.gdg.room.service;

import com.hackathon.gdg.game.domain.Game;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.global.security.IssuedToken;
import com.hackathon.gdg.global.security.TokenIssuer;
import com.hackathon.gdg.participant.domain.Participant;
import com.hackathon.gdg.participant.domain.ParticipantType;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.room.domain.Room;
import com.hackathon.gdg.room.domain.RoomStatus;
import com.hackathon.gdg.room.dto.CreateRoomRequest;
import com.hackathon.gdg.room.dto.CreateRoomResponse;
import com.hackathon.gdg.room.dto.JoinRoomRequest;
import com.hackathon.gdg.room.dto.JoinRoomResponse;
import com.hackathon.gdg.room.dto.ParticipantResponse;
import com.hackathon.gdg.room.dto.RoomResponse;
import com.hackathon.gdg.room.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class RoomService {

	private final RoomRepository roomRepository;
	private final ParticipantRepository participantRepository;
	private final GameRepository gameRepository;
	private final RoomCodeGenerator roomCodeGenerator;
	private final TokenIssuer tokenIssuer;
	private final String frontendBaseUrl;

	public RoomService(
			RoomRepository roomRepository,
			ParticipantRepository participantRepository,
			GameRepository gameRepository,
			RoomCodeGenerator roomCodeGenerator,
			TokenIssuer tokenIssuer,
			@Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl
	) {
		this.roomRepository = roomRepository;
		this.participantRepository = participantRepository;
		this.gameRepository = gameRepository;
		this.roomCodeGenerator = roomCodeGenerator;
		this.tokenIssuer = tokenIssuer;
		this.frontendBaseUrl = removeTrailingSlash(frontendBaseUrl);
	}

	@Transactional
	public CreateRoomResponse createRoom(CreateRoomRequest request) {
		IssuedToken hostToken = tokenIssuer.issue();
		Room room = roomRepository.save(Room.create(
				roomCodeGenerator.generateUnique(),
				request.name().trim(),
				hostToken.tokenHash()
		));
		Game game = gameRepository.save(Game.create(
				room,
				request.designDurationSeconds(),
				request.hideDurationSeconds(),
				request.seekDurationSeconds(),
				request.seekerCount()
		));

		return new CreateRoomResponse(
				room.getId(),
				game.getId(),
				room.getRoomCode(),
				hostToken.rawToken(),
				frontendBaseUrl + "/join/" + room.getRoomCode()
		);
	}

	@Transactional(readOnly = true)
	public RoomResponse getRoom(String roomCode) {
		Room room = findRoomByCode(roomCode);
		Game game = findCurrentGame(room.getId());
		return toRoomResponse(room, game);
	}

	@Transactional
	public JoinRoomResponse joinRoom(String roomCode, JoinRoomRequest request) {
		Room room = findRoomByCode(roomCode);
		if (room.getStatus() != RoomStatus.WAITING) {
			throw new ApiException(ErrorCode.ROOM_NOT_JOINABLE, HttpStatus.CONFLICT, "현재 참가할 수 없는 방입니다.");
		}

		String nickname = request.nickname().trim();
		if (participantRepository.existsByRoomIdAndNicknameIgnoreCase(room.getId(), nickname)) {
			throw duplicateNickname();
		}

		IssuedToken participantToken = tokenIssuer.issue();
		try {
			Participant participant = participantRepository.saveAndFlush(Participant.create(
					room,
					nickname,
					participantToken.tokenHash(),
					ParticipantType.PLAYER
			));
			Game game = findCurrentGame(room.getId());
			return new JoinRoomResponse(
					participant.getId(),
					participantToken.rawToken(),
					room.getId(),
					game.getId()
			);
		} catch (DataIntegrityViolationException exception) {
			throw duplicateNickname();
		}
	}

	@Transactional(readOnly = true)
	public List<ParticipantResponse> getParticipants(Long roomId, AuthenticatedActor actor) {
		if (actor == null || !actor.isHost() || !roomId.equals(actor.roomId())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "해당 방의 HOST만 조회할 수 있습니다.");
		}
		if (!roomRepository.existsById(roomId)) {
			throw new ApiException(ErrorCode.ROOM_NOT_FOUND, HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다.");
		}

		return participantRepository.findAllByRoomIdOrderByJoinedAtAsc(roomId).stream()
				.map(participant -> new ParticipantResponse(
						participant.getId(),
						participant.getNickname(),
						participant.getType(),
						participant.getGameRole(),
						participant.getStatus(),
						participant.getJoinedAt()
				))
				.toList();
	}

	private Room findRoomByCode(String roomCode) {
		return roomRepository.findByRoomCode(roomCode.toUpperCase(Locale.ROOT))
				.orElseThrow(() -> new ApiException(
						ErrorCode.ROOM_NOT_FOUND,
						HttpStatus.NOT_FOUND,
						"방을 찾을 수 없습니다."
				));
	}

	private Game findCurrentGame(Long roomId) {
		return gameRepository.findFirstByRoomIdOrderByCreatedAtDesc(roomId)
				.orElseThrow(() -> new ApiException(
						ErrorCode.INTERNAL_SERVER_ERROR,
						HttpStatus.INTERNAL_SERVER_ERROR,
						"방의 게임 설정을 찾을 수 없습니다."
				));
	}

	private RoomResponse toRoomResponse(Room room, Game game) {
		return new RoomResponse(
				room.getId(),
				game.getId(),
				room.getRoomCode(),
				room.getName(),
				room.getStatus(),
				game.getStatus(),
				game.getDesignDurationSeconds(),
				game.getHideDurationSeconds(),
				game.getSeekDurationSeconds(),
				game.getSeekerCount()
		);
	}

	private ApiException duplicateNickname() {
		return new ApiException(ErrorCode.DUPLICATE_NICKNAME, HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
	}

	private static String removeTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
