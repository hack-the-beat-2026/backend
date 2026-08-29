package com.hackathon.gdg.participant.domain;

import com.hackathon.gdg.room.domain.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
		name = "participants",
		uniqueConstraints = @UniqueConstraint(name = "uq_participants_token_hash", columnNames = "participant_token_hash")
)
public class Participant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private Room room;

	@Column(nullable = false, length = 30)
	private String nickname;

	@Column(name = "participant_token_hash", nullable = false, length = 64, unique = true)
	private String participantTokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ParticipantType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "game_role", nullable = false, length = 20)
	private GameRole gameRole;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ParticipantStatus status;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	protected Participant() {
	}

	private Participant(Room room, String nickname, String participantTokenHash, ParticipantType type) {
		this.room = Objects.requireNonNull(room);
		this.nickname = Objects.requireNonNull(nickname);
		this.participantTokenHash = Objects.requireNonNull(participantTokenHash);
		this.type = Objects.requireNonNull(type);
		this.gameRole = GameRole.NONE;
		this.status = ParticipantStatus.WAITING;
	}

	public static Participant create(Room room, String nickname, String participantTokenHash, ParticipantType type) {
		return new Participant(room, nickname, participantTokenHash, type);
	}

	public void assignRole(GameRole role) {
		if (type != ParticipantType.PLAYER || status != ParticipantStatus.WAITING || gameRole != GameRole.NONE) {
			throw new IllegalStateException("대기 중인 PLAYER에게만 역할을 배정할 수 있습니다.");
		}
		if (role == GameRole.NONE) {
			throw new IllegalArgumentException("NONE 역할은 배정할 수 없습니다.");
		}
		gameRole = role;
		status = ParticipantStatus.ACTIVE;
	}

	public void eliminate() {
		if (gameRole != GameRole.HIDER || status != ParticipantStatus.ACTIVE) {
			throw new IllegalStateException("ACTIVE HIDER만 탈락 처리할 수 있습니다.");
		}
		status = ParticipantStatus.ELIMINATED;
	}

	public void survive() {
		if (gameRole != GameRole.HIDER || status != ParticipantStatus.ACTIVE) {
			throw new IllegalStateException("ACTIVE HIDER만 생존 처리할 수 있습니다.");
		}
		status = ParticipantStatus.SURVIVED;
	}

	@PrePersist
	void onCreate() {
		joinedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Room getRoom() {
		return room;
	}

	public String getNickname() {
		return nickname;
	}

	public String getParticipantTokenHash() {
		return participantTokenHash;
	}

	public ParticipantType getType() {
		return type;
	}

	public GameRole getGameRole() {
		return gameRole;
	}

	public ParticipantStatus getStatus() {
		return status;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}
}
