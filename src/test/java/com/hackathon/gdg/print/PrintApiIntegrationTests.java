package com.hackathon.gdg.print;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.repository.CharacterRepository;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PrintApiIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ParticipantRepository participantRepository;

	@Autowired
	private CharacterRepository characterRepository;

	@Test
	void hostGetsStablePrintSlotsAndDecodableQrImages() throws Exception {
		StartedGame game = createStartedGame("Print", 3);
		for (JoinedPlayer hider : playersWithRole(game, GameRole.HIDER)) {
			submitCharacter(game.gameId(), hider.token());
		}
		List<Character> characters = characterRepository.findAllByGameIdOrderByIdAsc(game.gameId());

		mockMvc.perform(get("/api/v1/games/{gameId}/print-sheet", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paperSize").value("A4"))
				.andExpect(jsonPath("$.orientation").value("PORTRAIT"))
				.andExpect(jsonPath("$.duplexFlip").value("LONG_EDGE"))
				.andExpect(jsonPath("$.scalePercent").value(100))
				.andExpect(jsonPath("$.columns").value(3))
				.andExpect(jsonPath("$.characters[0].printSlot").value(1))
				.andExpect(jsonPath("$.characters[0].characterId").value(characters.get(0).getId()))
				.andExpect(jsonPath("$.characters[1].printSlot").value(2))
				.andExpect(jsonPath("$.characters[1].characterId").value(characters.get(1).getId()));

		for (Character character : characters) {
			MvcResult qrResult = mockMvc.perform(get(
							"/api/v1/games/{gameId}/characters/{characterId}/qr",
							game.gameId(),
							character.getId()
					)
						.header("Authorization", bearer(game.hostToken())))
					.andExpect(status().isOk())
					.andExpect(content().contentType(MediaType.IMAGE_PNG))
					.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
					.andReturn();
			assertEquals(
					"http://localhost:5173/c/" + character.getQrToken(),
					decodeQr(qrResult.getResponse().getContentAsByteArray())
			);
		}
	}

	@Test
	void printApisRequireOwningHost() throws Exception {
		StartedGame game = createStartedGame("Protected Print", 2);
		JoinedPlayer hider = playersWithRole(game, GameRole.HIDER).getFirst();
		submitCharacter(game.gameId(), hider.token());
		Character character = characterRepository.findAllByGameIdOrderByIdAsc(game.gameId()).getFirst();

		mockMvc.perform(get("/api/v1/games/{gameId}/print-sheet", game.gameId()))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/games/{gameId}/print-sheet", game.gameId())
						.header("Authorization", bearer(hider.token())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		StartedGame other = createStartedGame("Other Host", 2);
		mockMvc.perform(get(
						"/api/v1/games/{gameId}/characters/{characterId}/qr",
						game.gameId(),
						character.getId()
				)
						.header("Authorization", bearer(other.hostToken())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void printSheetIsUnavailableBeforeAllHidersSubmit() throws Exception {
		StartedGame game = createStartedGame("Not Ready", 3);
		JoinedPlayer firstHider = playersWithRole(game, GameRole.HIDER).getFirst();
		submitCharacter(game.gameId(), firstHider.token());

		mockMvc.perform(get("/api/v1/games/{gameId}/print-sheet", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME_INVALID_STATE"));
	}

	private StartedGame createStartedGame(String name, int playerCount) throws Exception {
		MvcResult roomResult = mockMvc.perform(post("/api/v1/rooms")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","designDurationSeconds":600,"hideDurationSeconds":300,"seekDurationSeconds":1200,"seekerCount":1}
								""".formatted(name)))
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
							.content("{\"nickname\":\"PrintPlayer%d\"}".formatted(index)))
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

	private void submitCharacter(Long gameId, String token) throws Exception {
		byte[] png = imageBytes("png");
		byte[] jpeg = imageBytes("jpeg");
		mockMvc.perform(multipart("/api/v1/games/{gameId}/characters", gameId)
						.file(new MockMultipartFile(
								"metadata",
								"metadata.json",
								MediaType.APPLICATION_JSON_VALUE,
								"{\"templateType\":\"STANDING_01\",\"scale\":1.0}".getBytes()
						))
						.file(new MockMultipartFile("originalPhoto", "original.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
						.file(new MockMultipartFile("characterImage", "character.png", MediaType.IMAGE_PNG_VALUE, png))
						.file(new MockMultipartFile("previewImage", "preview.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
						.header("Authorization", bearer(token)))
				.andExpect(status().isCreated());
	}

	private List<JoinedPlayer> playersWithRole(StartedGame game, GameRole role) {
		return game.players().stream()
				.filter(player -> participantRepository.findById(player.id()).orElseThrow().getGameRole() == role)
				.toList();
	}

	private byte[] imageBytes(String format) throws Exception {
		int type = format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage image = new BufferedImage(2, 2, type);
		image.setRGB(0, 0, Color.WHITE.getRGB());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assertTrue(ImageIO.write(image, format, output));
		return output.toByteArray();
	}

	private String decodeQr(byte[] png) throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
		return new MultiFormatReader().decode(bitmap).getText();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record StartedGame(Long gameId, String hostToken, List<JoinedPlayer> players) {
	}

	private record JoinedPlayer(Long id, String token) {
	}
}
