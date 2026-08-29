package com.hackathon.gdg.game.domain;

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

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "games")
public class Game {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private Room room;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private GameStatus status;

	@Column(name = "design_duration_seconds", nullable = false)
	private int designDurationSeconds;

	@Column(name = "hide_duration_seconds", nullable = false)
	private int hideDurationSeconds;

	@Column(name = "seek_duration_seconds", nullable = false)
	private int seekDurationSeconds;

	@Column(name = "seeker_count", nullable = false)
	private int seekerCount;

	@Column(name = "design_started_at")
	private Instant designStartedAt;

	@Column(name = "hide_started_at")
	private Instant hideStartedAt;

	@Column(name = "seek_started_at")
	private Instant seekStartedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Winner winner;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Game() {
	}

	private Game(Room room, int designDurationSeconds, int hideDurationSeconds, int seekDurationSeconds, int seekerCount) {
		this.room = Objects.requireNonNull(room);
		this.designDurationSeconds = designDurationSeconds;
		this.hideDurationSeconds = hideDurationSeconds;
		this.seekDurationSeconds = seekDurationSeconds;
		this.seekerCount = seekerCount;
		this.status = GameStatus.WAITING;
		this.winner = Winner.NONE;
	}

	public static Game create(Room room, int designDurationSeconds, int hideDurationSeconds, int seekDurationSeconds, int seekerCount) {
		return new Game(room, designDurationSeconds, hideDurationSeconds, seekDurationSeconds, seekerCount);
	}

	public void startDesigning(Instant startedAt) {
		if (status != GameStatus.WAITING) {
			throw new IllegalStateException("WAITING 상태의 게임만 시작할 수 있습니다.");
		}
		status = GameStatus.DESIGNING;
		designStartedAt = Objects.requireNonNull(startedAt);
	}

	public Instant getDesignEndsAt() {
		return designStartedAt == null ? null : designStartedAt.plusSeconds(designDurationSeconds);
	}

	public void completeDesigning() {
		if (status != GameStatus.DESIGNING) {
			throw new IllegalStateException("DESIGNING 상태의 게임만 인쇄 단계로 전환할 수 있습니다.");
		}
		status = GameStatus.PRINTING;
	}

	public void startHiding(Instant startedAt) {
		if (status != GameStatus.PRINTING) {
			throw new IllegalStateException("PRINTING 상태의 게임만 숨기기 단계로 전환할 수 있습니다.");
		}
		status = GameStatus.HIDING;
		hideStartedAt = Objects.requireNonNull(startedAt);
	}

	public Instant getHideEndsAt() {
		return hideStartedAt == null ? null : hideStartedAt.plusSeconds(hideDurationSeconds);
	}

	public void startSeeking(Instant startedAt) {
		if (status != GameStatus.HIDING) {
			throw new IllegalStateException("HIDING 상태의 게임만 탐색 단계로 전환할 수 있습니다.");
		}
		status = GameStatus.SEEKING;
		seekStartedAt = Objects.requireNonNull(startedAt);
	}

	public Instant getSeekEndsAt() {
		return seekStartedAt == null ? null : seekStartedAt.plusSeconds(seekDurationSeconds);
	}

	public void finish(Winner winner, Instant finishedAt) {
		if (status != GameStatus.SEEKING) {
			throw new IllegalStateException("SEEKING 상태의 게임만 종료할 수 있습니다.");
		}
		if (winner == Winner.NONE) {
			throw new IllegalArgumentException("게임 종료 시 승자가 필요합니다.");
		}
		status = GameStatus.FINISHED;
		this.winner = Objects.requireNonNull(winner);
		this.finishedAt = Objects.requireNonNull(finishedAt);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Room getRoom() {
		return room;
	}

	public GameStatus getStatus() {
		return status;
	}

	public int getDesignDurationSeconds() {
		return designDurationSeconds;
	}

	public int getHideDurationSeconds() {
		return hideDurationSeconds;
	}

	public int getSeekDurationSeconds() {
		return seekDurationSeconds;
	}

	public int getSeekerCount() {
		return seekerCount;
	}

	public Instant getDesignStartedAt() {
		return designStartedAt;
	}

	public Instant getHideStartedAt() {
		return hideStartedAt;
	}

	public Instant getSeekStartedAt() {
		return seekStartedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public Winner getWinner() {
		return winner;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
