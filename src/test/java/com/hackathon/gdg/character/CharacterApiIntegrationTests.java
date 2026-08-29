package com.hackathon.gdg.character;

import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CharacterApiIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ParticipantRepository participantRepository;

	@Autowired
	private CharacterRepository characterRepository;

	@Autowired
	private GameRepository gameRepository;

	@Test
	void hiderSubmitsImagesAndReadsTheirCharacter() throws Exception {
		StartedGame game = createStartedGame("Submit", 1, 2);
		JoinedPlayer hider = playerWithRole(game, GameRole.HIDER);

		MvcResult result = submitCharacter(game.gameId(), hider.token(), png(), png(), jpeg())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.participantId").value(hider.id()))
				.andExpect(jsonPath("$.templateType").value("STANDING_01"))
				.andExpect(jsonPath("$.characterImageUrl").value(org.hamcrest.Matchers.endsWith(".png")))
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.qrToken").isNotEmpty())
				.andReturn();

		String json = result.getResponse().getContentAsString();
		String characterUrl = JsonPath.read(json, "$.characterImageUrl");
		assertEquals(1, characterRepository.countByGameId(game.gameId()));
		assertNotNull(characterRepository.findByGameIdAndParticipantId(game.gameId(), hider.id()).orElseThrow());

		mockMvc.perform(get("/api/v1/games/{gameId}/characters/me", game.gameId())
						.header("Authorization", bearer(hider.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.participantId").value(hider.id()));

		mockMvc.perform(get(characterUrl))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG));
	}

	@Test
	void seekerCannotSubmitCharacter() throws Exception {
		StartedGame game = createStartedGame("Seeker", 1, 2);
		JoinedPlayer seeker = playerWithRole(game, GameRole.SEEKER);

		submitCharacter(game.gameId(), seeker.token(), png(), png(), jpeg())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INVALID_GAME_ROLE"));

		assertEquals(0, characterRepository.countByGameId(game.gameId()));
	}

	@Test
	void duplicateSubmissionIsRejected() throws Exception {
		StartedGame game = createStartedGame("Duplicate", 1, 3);
		JoinedPlayer hider = playerWithRole(game, GameRole.HIDER);
		submitCharacter(game.gameId(), hider.token(), png(), png(), jpeg())
				.andExpect(status().isCreated());

		submitCharacter(game.gameId(), hider.token(), png(), png(), jpeg())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CHARACTER_ALREADY_SUBMITTED"));
	}

	@Test
	void lastHiderSubmissionMovesGameToPrinting() throws Exception {
		StartedGame game = createStartedGame("Complete", 1, 3);
		List<JoinedPlayer> hiders = playersWithRole(game, GameRole.HIDER);

		submitCharacter(game.gameId(), hiders.get(0).token(), png(), png(), jpeg())
				.andExpect(status().isCreated());
		assertEquals(GameStatus.DESIGNING, gameRepository.findById(game.gameId()).orElseThrow().getStatus());

		submitCharacter(game.gameId(), hiders.get(1).token(), png(), png(), jpeg())
				.andExpect(status().isCreated());
		assertEquals(GameStatus.PRINTING, gameRepository.findById(game.gameId()).orElseThrow().getStatus());
	}

	@Test
	void onlyOwningHostCanListSubmittedCharacters() throws Exception {
		StartedGame game = createStartedGame("List", 1, 2);
		JoinedPlayer hider = playerWithRole(game, GameRole.HIDER);
		submitCharacter(game.gameId(), hider.token(), png(), png(), jpeg())
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/games/{gameId}/characters", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].participantId").value(hider.id()))
				.andExpect(jsonPath("$[0].qrToken").isNotEmpty());

		mockMvc.perform(get("/api/v1/games/{gameId}/characters", game.gameId())
						.header("Authorization", bearer(hider.token())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		StartedGame other = createStartedGame("Other", 1, 2);
		mockMvc.perform(get("/api/v1/games/{gameId}/characters", game.gameId())
						.header("Authorization", bearer(other.hostToken())))
				.andExpect(status().isForbidden());
	}

	@Test
	void characterImageMustActuallyBePng() throws Exception {
		StartedGame game = createStartedGame("Invalid Image", 1, 2);
		JoinedPlayer hider = playerWithRole(game, GameRole.HIDER);

		submitCharacter(game.gameId(), hider.token(), png(), jpeg(), jpeg())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
		assertFalse(characterRepository.existsByGameIdAndParticipantId(game.gameId(), hider.id()));
	}

	private org.springframework.test.web.servlet.ResultActions submitCharacter(
			Long gameId,
			String token,
			byte[] original,
			byte[] character,
			byte[] preview
	) throws Exception {
		MockMultipartFile metadata = new MockMultipartFile(
				"metadata",
				"metadata.json",
				MediaType.APPLICATION_JSON_VALUE,
				"""
						{"templateType":"STANDING_01","positionX":0.42,"positionY":0.58,"scale":0.7,"rotation":15}
						""".getBytes()
		);
		return mockMvc.perform(multipart("/api/v1/games/{gameId}/characters", gameId)
				.file(metadata)
				.file(new MockMultipartFile("originalPhoto", "original.png", MediaType.IMAGE_PNG_VALUE, original))
				.file(new MockMultipartFile("characterImage", "character.png", MediaType.IMAGE_PNG_VALUE, character))
				.file(new MockMultipartFile("previewImage", "preview.jpg", MediaType.IMAGE_JPEG_VALUE, preview))
				.header("Authorization", bearer(token)));
	}

	private StartedGame createStartedGame(String name, int seekerCount, int playerCount) throws Exception {
		MvcResult roomResult = mockMvc.perform(post("/api/v1/rooms")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","designDurationSeconds":600,"hideDurationSeconds":300,"seekDurationSeconds":1200,"seekerCount":%d}
								""".formatted(name, seekerCount)))
				.andExpect(status().isCreated())
				.andReturn();
		String roomJson = roomResult.getResponse().getContentAsString();
		Long roomId = ((Number) JsonPath.read(roomJson, "$.roomId")).longValue();
		Long gameId = ((Number) JsonPath.read(roomJson, "$.gameId")).longValue();
		String roomCode = JsonPath.read(roomJson, "$.roomCode");
		String hostToken = JsonPath.read(roomJson, "$.hostToken");

		List<JoinedPlayer> players = new ArrayList<>();
		for (int index = 0; index < playerCount; index++) {
			MvcResult joinResult = mockMvc.perform(post("/api/v1/rooms/{roomCode}/participants", roomCode)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"nickname\":\"Player-%d-%s\"}".formatted(index, name.replace(" ", ""))))
					.andExpect(status().isCreated())
					.andReturn();
			String joinJson = joinResult.getResponse().getContentAsString();
			players.add(new JoinedPlayer(
					((Number) JsonPath.read(joinJson, "$.participantId")).longValue(),
					JsonPath.read(joinJson, "$.participantToken")
			));
		}
		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", roomId)
						.header("Authorization", bearer(hostToken)))
				.andExpect(status().isOk());
		return new StartedGame(gameId, hostToken, players);
	}

	private JoinedPlayer playerWithRole(StartedGame game, GameRole role) {
		return playersWithRole(game, role).getFirst();
	}

	private List<JoinedPlayer> playersWithRole(StartedGame game, GameRole role) {
		return game.players().stream()
				.filter(player -> participantRepository.findById(player.id()).orElseThrow().getGameRole() == role)
				.toList();
	}

	private byte[] png() throws Exception {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, new Color(30, 120, 220, 120).getRGB());
		return imageBytes(image, "png");
	}

	private byte[] jpeg() throws Exception {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.WHITE.getRGB());
		return imageBytes(image, "jpeg");
	}

	private byte[] imageBytes(BufferedImage image, String format) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, format, output);
		return output.toByteArray();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record StartedGame(Long gameId, String hostToken, List<JoinedPlayer> players) {
	}

	private record JoinedPlayer(Long id, String token) {
	}
}
