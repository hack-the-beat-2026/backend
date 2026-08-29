package com.hackathon.gdg.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "rooms")
public class Room {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_code", nullable = false, length = 6, unique = true)
	private String roomCode;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "host_token_hash", nullable = false, length = 64, unique = true)
	private String hostTokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RoomStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Room() {
	}

	private Room(String roomCode, String name, String hostTokenHash) {
		this.roomCode = Objects.requireNonNull(roomCode);
		this.name = Objects.requireNonNull(name);
		this.hostTokenHash = Objects.requireNonNull(hostTokenHash);
		this.status = RoomStatus.WAITING;
	}

	public static Room create(String roomCode, String name, String hostTokenHash) {
		return new Room(roomCode, name, hostTokenHash);
	}

	public void startGame() {
		if (status != RoomStatus.WAITING) {
			throw new IllegalStateException("WAITING 상태의 방만 게임을 시작할 수 있습니다.");
		}
		status = RoomStatus.PLAYING;
	}

	public void finishGame() {
		if (status != RoomStatus.PLAYING) {
			throw new IllegalStateException("PLAYING 상태의 방만 종료할 수 있습니다.");
		}
		status = RoomStatus.FINISHED;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getRoomCode() {
		return roomCode;
	}

	public String getName() {
		return name;
	}

	public String getHostTokenHash() {
		return hostTokenHash;
	}

	public RoomStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
