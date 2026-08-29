package com.hackathon.gdg.room;

import com.hackathon.gdg.global.security.TokenService;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.room.repository.RoomRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoomApiIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RoomRepository roomRepository;

	@Autowired
	private ParticipantRepository participantRepository;

	@Autowired
	private TokenService tokenService;

	@Test
	void createsRoomAndStoresOnlyHostTokenHash() throws Exception {
		CreatedRoom created = createRoom("Birthday Party");

		assertEquals(6, created.roomCode().length());
		assertTrue(created.roomCode().matches("[A-Z0-9]{6}"));
		assertFalse(created.hostToken().isBlank());
		assertEquals(
				tokenService.hash(created.hostToken()),
				roomRepository.findById(created.roomId()).orElseThrow().getHostTokenHash()
		);
	}

	@Test
	void getsRoomByCaseInsensitiveCodeWithoutAuthentication() throws Exception {
		CreatedRoom created = createRoom("Birthday Party");

		mockMvc.perform(get("/api/v1/rooms/{roomCode}", created.roomCode().toLowerCase()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomId").value(created.roomId()))
				.andExpect(jsonPath("$.gameId").value(created.gameId()))
				.andExpect(jsonPath("$.roomCode").value(created.roomCode()))
				.andExpect(jsonPath("$.name").value("Birthday Party"))
				.andExpect(jsonPath("$.roomStatus").value("WAITING"))
				.andExpect(jsonPath("$.gameStatus").value("WAITING"))
				.andExpect(jsonPath("$.designDurationSeconds").value(600))
				.andExpect(jsonPath("$.seekerCount").value(1));
	}

	@Test
	void joinsRoomAndRejectsDuplicateNicknameIgnoringCase() throws Exception {
		CreatedRoom created = createRoom("Birthday Party");
		JoinedParticipant joined = joinRoom(created.roomCode(), "Player");

		assertEquals(
				tokenService.hash(joined.participantToken()),
				participantRepository.findById(joined.participantId()).orElseThrow().getParticipantTokenHash()
		);

		mockMvc.perform(post("/api/v1/rooms/{roomCode}/participants", created.roomCode())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nickname":"player"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
	}

	@Test
	void listsParticipantsOnlyWithHostTokenForSameRoom() throws Exception {
		CreatedRoom created = createRoom("First Room");
		JoinedParticipant joined = joinRoom(created.roomCode(), "Player One");

		mockMvc.perform(get("/api/v1/rooms/{roomId}/participants", created.roomId()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

		mockMvc.perform(get("/api/v1/rooms/{roomId}/participants", created.roomId())
						.header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

		mockMvc.perform(get("/api/v1/rooms/{roomId}/participants", created.roomId())
						.header("Authorization", "Bearer " + joined.participantToken()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		CreatedRoom otherRoom = createRoom("Other Room");
		mockMvc.perform(get("/api/v1/rooms/{roomId}/participants", created.roomId())
						.header("Authorization", "Bearer " + otherRoom.hostToken()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		MvcResult result = mockMvc.perform(get("/api/v1/rooms/{roomId}/participants", created.roomId())
						.header("Authorization", "Bearer " + created.hostToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].participantId").value(joined.participantId()))
				.andExpect(jsonPath("$[0].nickname").value("Player One"))
				.andExpect(jsonPath("$[0].type").value("PLAYER"))
				.andExpect(jsonPath("$[0].gameRole").value("NONE"))
				.andReturn();

		assertFalse(result.getResponse().getContentAsString().contains("participantToken"));
	}

	@Test
	void rejectsInvalidCreateRequestWithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/v1/rooms")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name":" ",
								  "designDurationSeconds":0,
								  "hideDurationSeconds":300,
								  "seekDurationSeconds":1200,
								  "seekerCount":0
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.fieldErrors.name").exists())
				.andExpect(jsonPath("$.fieldErrors.designDurationSeconds").exists())
				.andExpect(jsonPath("$.fieldErrors.seekerCount").exists());
	}

	@Test
	void returnsNotFoundForUnknownRoomCode() throws Exception {
		mockMvc.perform(get("/api/v1/rooms/ZZZZZZ"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
	}

	private CreatedRoom createRoom(String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/rooms")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name":"%s",
								  "designDurationSeconds":600,
								  "hideDurationSeconds":300,
								  "seekDurationSeconds":1200,
								  "seekerCount":1
								}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roomCode").isString())
				.andExpect(jsonPath("$.hostToken").isString())
				.andExpect(jsonPath("$.joinUrl").isString())
				.andReturn();

		String json = result.getResponse().getContentAsString();
		return new CreatedRoom(
				((Number) JsonPath.read(json, "$.roomId")).longValue(),
				((Number) JsonPath.read(json, "$.gameId")).longValue(),
				JsonPath.read(json, "$.roomCode"),
				JsonPath.read(json, "$.hostToken")
		);
	}

	private JoinedParticipant joinRoom(String roomCode, String nickname) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/rooms/{roomCode}/participants", roomCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nickname":"%s"}
								""".formatted(nickname)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.participantToken").isString())
				.andReturn();

		String json = result.getResponse().getContentAsString();
		return new JoinedParticipant(
				((Number) JsonPath.read(json, "$.participantId")).longValue(),
				JsonPath.read(json, "$.participantToken")
		);
	}

	private record CreatedRoom(Long roomId, Long gameId, String roomCode, String hostToken) {
	}

	private record JoinedParticipant(Long participantId, String participantToken) {
	}
}
