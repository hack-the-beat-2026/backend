package com.hackathon.gdg.game;

import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.domain.CharacterStatus;
import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.domain.Winner;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.domain.ParticipantStatus;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.room.domain.RoomStatus;
import com.hackathon.gdg.room.repository.RoomRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GameFlowIntegrationTests.ClockTestConfig.class)
class GameFlowIntegrationTests {

	private static final Instant START_TIME = Instant.parse("2026-08-29T08:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private RoomRepository roomRepository;

	@Autowired
	private ParticipantRepository participantRepository;

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private CharacterRepository characterRepository;

	@BeforeEach
	void setUp() {
		cleanDatabase();
		clock.set(START_TIME);
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void completesHidingSeekingScanningAndSeekerWinFlow() throws Exception {
		StartedGame game = createStartedGame("Full Flow", 3);
		List<JoinedPlayer> hiders = playersWithRole(game, GameRole.HIDER);
		JoinedPlayer seeker = playersWithRole(game, GameRole.SEEKER).getFirst();
		submitAllHiders(game);

		mockMvc.perform(post("/api/v1/games/{gameId}/hiding/start", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("HIDING"))
				.andExpect(jsonPath("$.hideStartedAt").value(START_TIME.toString()))
				.andExpect(jsonPath("$.hideEndsAt").value(START_TIME.plusSeconds(30).toString()));
		assertTrue(characterRepository.findAllByGameIdOrderByIdAsc(game.gameId()).stream()
				.allMatch(character -> character.getStatus() == CharacterStatus.PRINTED && character.getPrintedAt() != null));

		Character first = characterFor(game, hiders.get(0));
		Character second = characterFor(game, hiders.get(1));
		markHidden(game, hiders.get(0), first.getId());
		mockMvc.perform(post("/api/v1/games/{gameId}/characters/{characterId}/hidden", game.gameId(), first.getId())
						.header("Authorization", bearer(hiders.get(0).token())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CHARACTER_ALREADY_HIDDEN"));

		clock.advanceSeconds(30);
		mockMvc.perform(post("/api/v1/games/{gameId}/seeking/start", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("HIDERS_NOT_READY"));
		markHidden(game, hiders.get(1), second.getId());

		mockMvc.perform(post("/api/v1/games/{gameId}/seeking/start", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SEEKING"))
				.andExpect(jsonPath("$.seekEndsAt").value(clock.instant().plusSeconds(60).toString()));

		mockMvc.perform(get("/api/v1/characters/qr/{qrToken}", first.getQrToken())
						.header("Authorization", bearer(seeker.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.characterId").value(first.getId()))
				.andExpect(jsonPath("$.status").value("HIDDEN"));
		mockMvc.perform(get("/api/v1/characters/qr/{qrToken}", first.getQrToken())
						.header("Authorization", bearer(hiders.get(0).token())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INVALID_GAME_ROLE"));

		clock.advanceSeconds(12);
		mockMvc.perform(post("/api/v1/games/{gameId}/characters/{qrToken}/found", game.gameId(), first.getQrToken())
						.header("Authorization", bearer(seeker.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hiderNickname").value(hiders.get(0).nickname()))
				.andExpect(jsonPath("$.survivalSeconds").value(12))
				.andExpect(jsonPath("$.gameFinished").value(false))
				.andExpect(jsonPath("$.winner").value("NONE"));
		assertEquals(ParticipantStatus.ELIMINATED,
				participantRepository.findById(hiders.get(0).id()).orElseThrow().getStatus());

		mockMvc.perform(post("/api/v1/games/{gameId}/characters/{qrToken}/found", game.gameId(), first.getQrToken())
						.header("Authorization", bearer(seeker.token())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CHARACTER_ALREADY_FOUND"));

		clock.advanceSeconds(8);
		mockMvc.perform(post("/api/v1/games/{gameId}/characters/{qrToken}/found", game.gameId(), second.getQrToken())
						.header("Authorization", bearer(seeker.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.survivalSeconds").value(20))
				.andExpect(jsonPath("$.gameFinished").value(true))
				.andExpect(jsonPath("$.winner").value("SEEKER"));

		assertEquals(GameStatus.FINISHED, gameRepository.findById(game.gameId()).orElseThrow().getStatus());
		assertEquals(Winner.SEEKER, gameRepository.findById(game.gameId()).orElseThrow().getWinner());
		assertEquals(RoomStatus.FINISHED, roomRepository.findById(game.roomId()).orElseThrow().getStatus());

		mockMvc.perform(get("/api/v1/games/{gameId}/result", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.winner").value("SEEKER"))
				.andExpect(jsonPath("$.hiders[0].participantId").value(hiders.get(1).id()))
				.andExpect(jsonPath("$.hiders[0].survivalSeconds").value(20))
				.andExpect(jsonPath("$.hiders[1].survivalSeconds").value(12))
				.andExpect(jsonPath("$.seekers[0].participantId").value(seeker.id()))
				.andExpect(jsonPath("$.seekers[0].foundCount").value(2));
	}

	@Test
	void enforcesHideTimerAndFinishesWithHiderWinAtSeekDeadline() throws Exception {
		StartedGame game = createStartedGame("Timer Flow", 3);
		List<JoinedPlayer> hiders = playersWithRole(game, GameRole.HIDER);
		JoinedPlayer seeker = playersWithRole(game, GameRole.SEEKER).getFirst();
		submitAllHiders(game);
		startHiding(game);
		for (JoinedPlayer hider : hiders) {
			markHidden(game, hider, characterFor(game, hider).getId());
		}

		mockMvc.perform(post("/api/v1/games/{gameId}/seeking/start", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("HIDE_TIME_NOT_EXPIRED"));

		clock.advanceSeconds(30);
		startSeeking(game);
		mockMvc.perform(post("/api/v1/games/{gameId}/finish", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEEK_TIME_NOT_EXPIRED"));

		clock.advanceSeconds(60);
		String survivorQrToken = characterFor(game, hiders.getFirst()).getQrToken();
		mockMvc.perform(post("/api/v1/games/{gameId}/characters/{qrToken}/found", game.gameId(), survivorQrToken)
						.header("Authorization", bearer(seeker.token())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME_INVALID_STATE"));

		mockMvc.perform(post("/api/v1/games/{gameId}/finish", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FINISHED"))
				.andExpect(jsonPath("$.winner").value("HIDER"));

		assertTrue(characterRepository.findAllByGameIdOrderByIdAsc(game.gameId()).stream()
				.allMatch(character -> character.getStatus() == CharacterStatus.SURVIVED));
		assertTrue(hiders.stream().allMatch(hider ->
				participantRepository.findById(hider.id()).orElseThrow().getStatus() == ParticipantStatus.SURVIVED));

		mockMvc.perform(get("/api/v1/games/{gameId}/result", game.gameId())
						.header("Authorization", bearer(hiders.getFirst().token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.winner").value("HIDER"))
				.andExpect(jsonPath("$.hiders[0].survivalSeconds").value(60))
				.andExpect(jsonPath("$.hiders[1].survivalSeconds").value(60));
	}

	@Test
	void concurrentDuplicateScanHasExactlyOneSuccess() throws Exception {
		StartedGame game = createStartedGame("Concurrent Scan", 3);
		List<JoinedPlayer> hiders = playersWithRole(game, GameRole.HIDER);
		JoinedPlayer seeker = playersWithRole(game, GameRole.SEEKER).getFirst();
		submitAllHiders(game);
		startHiding(game);
		for (JoinedPlayer hider : hiders) {
			markHidden(game, hider, characterFor(game, hider).getId());
		}
		clock.advanceSeconds(30);
		startSeeking(game);
		String qrToken = characterFor(game, hiders.getFirst()).getQrToken();

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<MvcResult>> futures = new ArrayList<>();
			for (int index = 0; index < 2; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return mockMvc.perform(post(
								"/api/v1/games/{gameId}/characters/{qrToken}/found",
								game.gameId(),
								qrToken
						)
							.header("Authorization", bearer(seeker.token())))
							.andReturn();
				}));
			}
			ready.await();
			start.countDown();
			List<MvcResult> results = futures.stream().map(future -> {
				try {
					return future.get();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
			}).toList();
			assertEquals(1, results.stream().filter(result -> result.getResponse().getStatus() == 200).count());
			assertEquals(1, results.stream().filter(result -> result.getResponse().getStatus() == 409).count());
			MvcResult conflict = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
			assertEquals("CHARACTER_ALREADY_FOUND", JsonPath.read(conflict.getResponse().getContentAsString(), "$.code"));
		} finally {
			executor.shutdownNow();
		}

		assertEquals(1, characterRepository.findAllByGameIdOrderByIdAsc(game.gameId()).stream()
				.filter(character -> character.getStatus() == CharacterStatus.FOUND)
				.count());
	}

	@Test
	void rejectsInvalidQrAndQrFromAnotherGame() throws Exception {
		StartedGame first = createStartedGame("First Scan Game", 3);
		List<JoinedPlayer> firstHiders = playersWithRole(first, GameRole.HIDER);
		JoinedPlayer firstSeeker = playersWithRole(first, GameRole.SEEKER).getFirst();
		submitAllHiders(first);
		startHiding(first);
		for (JoinedPlayer hider : firstHiders) {
			markHidden(first, hider, characterFor(first, hider).getId());
		}
		clock.advanceSeconds(30);
		startSeeking(first);

		StartedGame second = createStartedGame("Second Scan Game", 2);
		submitAllHiders(second);
		String otherGameQr = characterFor(second, playersWithRole(second, GameRole.HIDER).getFirst()).getQrToken();

		mockMvc.perform(post("/api/v1/games/{gameId}/characters/{qrToken}/found", first.gameId(), otherGameQr)
						.header("Authorization", bearer(firstSeeker.token())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("INVALID_QR_TOKEN"));

		mockMvc.perform(get("/api/v1/characters/qr/{qrToken}", "not-a-valid-token")
						.header("Authorization", bearer(firstSeeker.token())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("INVALID_QR_TOKEN"));
	}

	private StartedGame createStartedGame(String name, int playerCount) throws Exception {
		MvcResult roomResult = mockMvc.perform(post("/api/v1/rooms")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","designDurationSeconds":600,"hideDurationSeconds":30,"seekDurationSeconds":60,"seekerCount":1}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		String json = roomResult.getResponse().getContentAsString();
		Long roomId = ((Number) JsonPath.read(json, "$.roomId")).longValue();
		Long gameId = ((Number) JsonPath.read(json, "$.gameId")).longValue();
		String roomCode = JsonPath.read(json, "$.roomCode");
		String hostToken = JsonPath.read(json, "$.hostToken");

		List<JoinedPlayer> players = new ArrayList<>();
		for (int index = 0; index < playerCount; index++) {
			MvcResult join = mockMvc.perform(post("/api/v1/rooms/{roomCode}/participants", roomCode)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"nickname\":\"FlowPlayer%d\"}".formatted(index)))
					.andExpect(status().isCreated())
					.andReturn();
			String joinJson = join.getResponse().getContentAsString();
			players.add(new JoinedPlayer(
					((Number) JsonPath.read(joinJson, "$.participantId")).longValue(),
					JsonPath.read(joinJson, "$.participantToken"),
					"FlowPlayer" + index
			));
		}
		mockMvc.perform(post("/api/v1/rooms/{roomId}/games/start", roomId)
						.header("Authorization", bearer(hostToken)))
				.andExpect(status().isOk());
		return new StartedGame(roomId, gameId, hostToken, players);
	}

	private void submitAllHiders(StartedGame game) throws Exception {
		for (JoinedPlayer hider : playersWithRole(game, GameRole.HIDER)) {
			submitCharacter(game.gameId(), hider.token());
		}
	}

	private void submitCharacter(Long gameId, String token) throws Exception {
		byte[] png = imageBytes("png");
		byte[] jpeg = imageBytes("jpeg");
		mockMvc.perform(multipart("/api/v1/games/{gameId}/characters", gameId)
						.file(new MockMultipartFile("metadata", "metadata.json", MediaType.APPLICATION_JSON_VALUE,
								"{\"templateType\":\"STANDING_01\",\"scale\":1.0}".getBytes()))
						.file(new MockMultipartFile("originalPhoto", "original.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
						.file(new MockMultipartFile("characterImage", "character.png", MediaType.IMAGE_PNG_VALUE, png))
						.file(new MockMultipartFile("previewImage", "preview.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
						.header("Authorization", bearer(token)))
				.andExpect(status().isCreated());
	}

	private void startHiding(StartedGame game) throws Exception {
		mockMvc.perform(post("/api/v1/games/{gameId}/hiding/start", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isOk());
	}

	private void markHidden(StartedGame game, JoinedPlayer hider, Long characterId) throws Exception {
		mockMvc.perform(post("/api/v1/games/{gameId}/characters/{characterId}/hidden", game.gameId(), characterId)
						.header("Authorization", bearer(hider.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("HIDDEN"));
	}

	private void startSeeking(StartedGame game) throws Exception {
		mockMvc.perform(post("/api/v1/games/{gameId}/seeking/start", game.gameId())
						.header("Authorization", bearer(game.hostToken())))
				.andExpect(status().isOk());
	}

	private Character characterFor(StartedGame game, JoinedPlayer player) {
		return characterRepository.findByGameIdAndParticipantId(game.gameId(), player.id()).orElseThrow();
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
		ImageIO.write(image, format, output);
		return output.toByteArray();
	}

	private void cleanDatabase() {
		characterRepository.deleteAll();
		gameRepository.deleteAll();
		participantRepository.deleteAll();
		roomRepository.deleteAll();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record StartedGame(Long roomId, Long gameId, String hostToken, List<JoinedPlayer> players) {
	}

	private record JoinedPlayer(Long id, String token, String nickname) {
	}

	@TestConfiguration
	static class ClockTestConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(START_TIME);
		}
	}

	static class MutableClock extends Clock {

		private final AtomicReference<Instant> instant;

		MutableClock(Instant initial) {
			this.instant = new AtomicReference<>(initial);
		}

		void set(Instant value) {
			instant.set(value);
		}

		void advanceSeconds(long seconds) {
			instant.updateAndGet(value -> value.plusSeconds(seconds));
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(instant(), zone);
		}

		@Override
		public Instant instant() {
			return instant.get();
		}
	}
}
