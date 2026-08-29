package com.hackathon.gdg.game;

import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.domain.ParticipantStatus;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.room.domain.RoomStatus;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GameApiIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RoomRepository roomRepository;

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private ParticipantRepository participantRepository;

	@Test
	void hostStartsGameAndAssignsConfiguredNumberOfSeekers() throws Exception {
		CreatedRoom room = createRoom("Role Test", 2);
		List<JoinedPlayer> players = joinPlayers(room.roomCode(), 4);

		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", room.roomId())
						.header("Authorization", bearer(room.hostToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gameId").value(room.gameId()))
				.andExpect(jsonPath("$.status").value("DESIGNING"))
				.andExpect(jsonPath("$.myRole").value("NONE"))
				.andExpect(jsonPath("$.myParticipantStatus").doesNotExist())
				.andExpect(jsonPath("$.seekerCount").value(2))
				.andExpect(jsonPath("$.hiderCount").value(2))
				.andExpect(jsonPath("$.designStartedAt").isString())
				.andExpect(jsonPath("$.designEndsAt").isString());

		assertEquals(RoomStatus.PLAYING, roomRepository.findById(room.roomId()).orElseThrow().getStatus());
		assertEquals(GameStatus.DESIGNING, gameRepository.findById(room.gameId()).orElseThrow().getStatus());
		assertNotNull(gameRepository.findById(room.gameId()).orElseThrow().getDesignStartedAt());
		assertEquals(2, participantRepository.countByRoomIdAndGameRole(room.roomId(), GameRole.SEEKER));
		assertEquals(2, participantRepository.countByRoomIdAndGameRole(room.roomId(), GameRole.HIDER));
		assertTrue(players.stream()
				.map(player -> participantRepository.findById(player.participantId()).orElseThrow())
				.allMatch(participant -> participant.getStatus() == ParticipantStatus.ACTIVE));
	}

	@Test
	void eachPlayerCanReadOnlyTheirOwnAssignedRole() throws Exception {
		CreatedRoom room = createRoom("Private Role Test", 1);
		List<JoinedPlayer> players = joinPlayers(room.roomCode(), 3);
		startGame(room);

		for (JoinedPlayer player : players) {
			GameRole expectedRole = participantRepository.findById(player.participantId()).orElseThrow().getGameRole();
			mockMvc.perform(get("/api/v1/games/{gameId}", room.gameId())
							.header("Authorization", bearer(player.participantToken())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.myRole").value(expectedRole.name()))
					.andExpect(jsonPath("$.myParticipantStatus").value("ACTIVE"))
					.andExpect(jsonPath("$.status").value("DESIGNING"));
		}
	}

	@Test
	void rejectsStartWithoutHostOwnership() throws Exception {
		CreatedRoom room = createRoom("Protected Start", 1);
		JoinedPlayer player = joinPlayers(room.roomCode(), 2).getFirst();

		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", room.roomId()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", room.roomId())
						.header("Authorization", bearer(player.participantToken())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		CreatedRoom otherRoom = createRoom("Other Host", 1);
		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", room.roomId())
						.header("Authorization", bearer(otherRoom.hostToken())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void rejectsStartWhenParticipantsAreInsufficient() throws Exception {
		CreatedRoom room = createRoom("Small Room", 1);
		joinPlayers(room.roomCode(), 1);

		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", room.roomId())
						.header("Authorization", bearer(room.hostToken())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_PARTICIPANTS"));
	}

	@Test
	void rejectsStartWhenSeekerCountIsNotLessThanPlayerCount() throws Exception {
		CreatedRoom room = createRoom("Invalid Seeker Count", 2);
		joinPlayers(room.roomCode(), 2);

		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", room.roomId())
						.header("Authorization", bearer(room.hostToken())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_SEEKER_COUNT"));
	}

	@Test
	void rejectsStartingSameGameTwice() throws Exception {
		CreatedRoom room = createRoom("Duplicate Start", 1);
		joinPlayers(room.roomCode(), 3);
		startGame(room);

		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", room.roomId())
						.header("Authorization", bearer(room.hostToken())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME_INVALID_STATE"));
	}

	@Test
	void deniesGameLookupFromAnotherRoom() throws Exception {
		CreatedRoom room = createRoom("First Game", 1);
		joinPlayers(room.roomCode(), 2);
		startGame(room);
		CreatedRoom otherRoom = createRoom("Second Game", 1);

		mockMvc.perform(get("/api/v1/games/{gameId}", room.gameId())
						.header("Authorization", bearer(otherRoom.hostToken())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	private void startGame(CreatedRoom room) throws Exception {
		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", room.roomId())
						.header("Authorization", bearer(room.hostToken())))
				.andExpect(status().isOk());
	}

	private CreatedRoom createRoom(String name, int seekerCount) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/rooms")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name":"%s",
								  "designDurationSeconds":600,
								  "hideDurationSeconds":300,
								  "seekDurationSeconds":1200,
								  "seekerCount":%d
								}
								""".formatted(name, seekerCount)))
				.andExpect(status().isCreated())
				.andReturn();

		String json = result.getResponse().getContentAsString();
		return new CreatedRoom(
				((Number) JsonPath.read(json, "$.roomId")).longValue(),
				((Number) JsonPath.read(json, "$.gameId")).longValue(),
				JsonPath.read(json, "$.roomCode"),
				JsonPath.read(json, "$.hostToken")
		);
	}

	private List<JoinedPlayer> joinPlayers(String roomCode, int count) throws Exception {
		List<JoinedPlayer> players = new ArrayList<>();
		for (int index = 1; index <= count; index++) {
			MvcResult result = mockMvc.perform(post("/api/v1/rooms/{roomCode}/participants", roomCode)
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"nickname":"Player%d"}
									""".formatted(index)))
					.andExpect(status().isCreated())
					.andReturn();
			String json = result.getResponse().getContentAsString();
			players.add(new JoinedPlayer(
					((Number) JsonPath.read(json, "$.participantId")).longValue(),
					JsonPath.read(json, "$.participantToken")
			));
		}
		return players;
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record CreatedRoom(Long roomId, Long gameId, String roomCode, String hostToken) {
	}

	private record JoinedPlayer(Long participantId, String participantToken) {
	}
}
