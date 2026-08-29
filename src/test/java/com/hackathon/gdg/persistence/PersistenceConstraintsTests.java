package com.hackathon.gdg.persistence;

import com.hackathon.gdg.character.domain.Character;
import com.hackathon.gdg.character.domain.CharacterStatus;
import com.hackathon.gdg.character.repository.CharacterRepository;
import com.hackathon.gdg.game.domain.Game;
import com.hackathon.gdg.game.domain.GameStatus;
import com.hackathon.gdg.game.repository.GameRepository;
import com.hackathon.gdg.participant.domain.Participant;
import com.hackathon.gdg.participant.domain.ParticipantType;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.room.domain.Room;
import com.hackathon.gdg.room.domain.RoomStatus;
import com.hackathon.gdg.room.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceConstraintsTests {

	@Autowired
	private RoomRepository roomRepository;

	@Autowired
	private ParticipantRepository participantRepository;

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private CharacterRepository characterRepository;

	@Test
	void persistsAndQueriesCoreDomainGraph() {
		Room room = saveRoom("ABC123", tokenHash('a'));
		Participant hider = saveParticipant(room, "Hider", tokenHash('b'));
		Game game = saveGame(room);
		Character character = saveCharacter(game, hider, "qr-token-1");

		assertNotNull(room.getCreatedAt());
		assertNotNull(hider.getJoinedAt());
		assertNotNull(game.getCreatedAt());
		assertNotNull(character.getSubmittedAt());
		assertEquals(RoomStatus.WAITING, room.getStatus());
		assertEquals(GameStatus.WAITING, game.getStatus());
		assertEquals(CharacterStatus.SUBMITTED, character.getStatus());
		assertEquals(room.getId(), roomRepository.findByRoomCode("ABC123").orElseThrow().getId());
		assertEquals(hider.getId(), participantRepository.findByParticipantTokenHash(tokenHash('b')).orElseThrow().getId());
		assertEquals(game.getId(), gameRepository.findFirstByRoomIdOrderByCreatedAtDesc(room.getId()).orElseThrow().getId());
		assertEquals(character.getId(), characterRepository.findByQrToken("qr-token-1").orElseThrow().getId());
	}

	@Test
	void rejectsDuplicateRoomCode() {
		saveRoom("ABC123", tokenHash('a'));

		Room duplicate = Room.create("ABC123", "Another Room", tokenHash('b'));

		assertThrows(DataIntegrityViolationException.class, () -> roomRepository.saveAndFlush(duplicate));
	}

	@Test
	void rejectsInvalidRoomCodeFormat() {
		Room invalid = Room.create("abc123", "Invalid Room", tokenHash('a'));

		assertThrows(DataIntegrityViolationException.class, () -> roomRepository.saveAndFlush(invalid));
	}

	@Test
	void rejectsDuplicateNicknameIgnoringCaseWithinRoom() {
		Room room = saveRoom("ABC123", tokenHash('a'));
		saveParticipant(room, "Player", tokenHash('b'));

		Participant duplicate = Participant.create(room, "player", tokenHash('c'), ParticipantType.PLAYER);

		assertTrue(participantRepository.existsByRoomIdAndNicknameIgnoreCase(room.getId(), "PLAYER"));
		assertThrows(DataIntegrityViolationException.class, () -> participantRepository.saveAndFlush(duplicate));
	}

	@Test
	void rejectsDuplicateParticipantTokenHash() {
		Room firstRoom = saveRoom("ABC123", tokenHash('a'));
		Room secondRoom = saveRoom("DEF456", tokenHash('d'));
		saveParticipant(firstRoom, "First", tokenHash('b'));

		Participant duplicate = Participant.create(secondRoom, "Second", tokenHash('b'), ParticipantType.PLAYER);

		assertThrows(DataIntegrityViolationException.class, () -> participantRepository.saveAndFlush(duplicate));
	}

	@Test
	void rejectsMoreThanOneCharacterPerHiderAndGame() {
		Room room = saveRoom("ABC123", tokenHash('a'));
		Participant hider = saveParticipant(room, "Hider", tokenHash('b'));
		Game game = saveGame(room);
		saveCharacter(game, hider, "qr-token-1");

		Character duplicate = newCharacter(game, hider, "qr-token-2");

		assertThrows(DataIntegrityViolationException.class, () -> characterRepository.saveAndFlush(duplicate));
	}

	@Test
	void rejectsDuplicateQrTokenAcrossCharacters() {
		Room room = saveRoom("ABC123", tokenHash('a'));
		Participant firstHider = saveParticipant(room, "First", tokenHash('b'));
		Participant secondHider = saveParticipant(room, "Second", tokenHash('c'));
		Game game = saveGame(room);
		saveCharacter(game, firstHider, "same-qr-token");

		Character duplicate = newCharacter(game, secondHider, "same-qr-token");

		assertThrows(DataIntegrityViolationException.class, () -> characterRepository.saveAndFlush(duplicate));
	}

	private Room saveRoom(String roomCode, String hostTokenHash) {
		return roomRepository.saveAndFlush(Room.create(roomCode, "Party Room", hostTokenHash));
	}

	private Participant saveParticipant(Room room, String nickname, String participantTokenHash) {
		return participantRepository.saveAndFlush(
				Participant.create(room, nickname, participantTokenHash, ParticipantType.PLAYER)
		);
	}

	private Game saveGame(Room room) {
		return gameRepository.saveAndFlush(Game.create(room, 600, 300, 1200, 1));
	}

	private Character saveCharacter(Game game, Participant participant, String qrToken) {
		return characterRepository.saveAndFlush(newCharacter(game, participant, qrToken));
	}

	private Character newCharacter(Game game, Participant participant, String qrToken) {
		return Character.submit(
				game,
				participant,
				"STANDING_01",
				"/images/original.jpg",
				"/images/character.png",
				"/images/preview.jpg",
				0.42,
				0.58,
				0.7,
				15.0,
				qrToken
		);
	}

	private String tokenHash(char character) {
		return String.valueOf(character).repeat(64);
	}
}
