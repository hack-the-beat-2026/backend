package com.hackathon.gdg.character.domain;

import com.hackathon.gdg.game.domain.Game;
import com.hackathon.gdg.participant.domain.Participant;
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
		name = "characters",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_characters_game_participant", columnNames = {"game_id", "participant_id"}),
				@UniqueConstraint(name = "uq_characters_qr_token", columnNames = "qr_token")
		}
)
public class Character {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id", nullable = false)
	private Game game;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "participant_id", nullable = false)
	private Participant participant;

	@Column(name = "template_type", nullable = false, length = 50)
	private String templateType;

	@Column(name = "original_photo_url", nullable = false, columnDefinition = "text")
	private String originalPhotoUrl;

	@Column(name = "character_image_url", nullable = false, columnDefinition = "text")
	private String characterImageUrl;

	@Column(name = "preview_image_url", nullable = false, columnDefinition = "text")
	private String previewImageUrl;

	@Column(name = "position_x")
	private Double positionX;

	@Column(name = "position_y")
	private Double positionY;

	private Double scale;

	private Double rotation;

	@Column(name = "qr_token", nullable = false, length = 255, unique = true)
	private String qrToken;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CharacterStatus status;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "printed_at")
	private Instant printedAt;

	@Column(name = "found_at")
	private Instant foundAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "found_by_participant_id")
	private Participant foundByParticipant;

	protected Character() {
	}

	private Character(
			Game game,
			Participant participant,
			String templateType,
			String originalPhotoUrl,
			String characterImageUrl,
			String previewImageUrl,
			Double positionX,
			Double positionY,
			Double scale,
			Double rotation,
			String qrToken
	) {
		this.game = Objects.requireNonNull(game);
		this.participant = Objects.requireNonNull(participant);
		this.templateType = Objects.requireNonNull(templateType);
		this.originalPhotoUrl = Objects.requireNonNull(originalPhotoUrl);
		this.characterImageUrl = Objects.requireNonNull(characterImageUrl);
		this.previewImageUrl = Objects.requireNonNull(previewImageUrl);
		this.positionX = positionX;
		this.positionY = positionY;
		this.scale = scale;
		this.rotation = rotation;
		this.qrToken = Objects.requireNonNull(qrToken);
		this.status = CharacterStatus.SUBMITTED;
	}

	public static Character submit(
			Game game,
			Participant participant,
			String templateType,
			String originalPhotoUrl,
			String characterImageUrl,
			String previewImageUrl,
			Double positionX,
			Double positionY,
			Double scale,
			Double rotation,
			String qrToken
	) {
		return new Character(
				game,
				participant,
				templateType,
				originalPhotoUrl,
				characterImageUrl,
				previewImageUrl,
				positionX,
				positionY,
				scale,
				rotation,
				qrToken
		);
	}

	@PrePersist
	void onCreate() {
		submittedAt = Instant.now();
	}

	public void markPrinted(Instant printedAt) {
		if (status != CharacterStatus.SUBMITTED) {
			throw new IllegalStateException("SUBMITTED Character만 인쇄 완료할 수 있습니다.");
		}
		status = CharacterStatus.PRINTED;
		this.printedAt = Objects.requireNonNull(printedAt);
	}

	public void markHidden() {
		if (status != CharacterStatus.PRINTED) {
			throw new IllegalStateException("PRINTED Character만 숨기기 완료할 수 있습니다.");
		}
		status = CharacterStatus.HIDDEN;
	}

	public void markFound(Participant seeker, Instant foundAt) {
		if (status != CharacterStatus.HIDDEN) {
			throw new IllegalStateException("HIDDEN Character만 발견 처리할 수 있습니다.");
		}
		status = CharacterStatus.FOUND;
		this.foundByParticipant = Objects.requireNonNull(seeker);
		this.foundAt = Objects.requireNonNull(foundAt);
	}

	public void markSurvived() {
		if (status != CharacterStatus.HIDDEN) {
			throw new IllegalStateException("HIDDEN Character만 생존 처리할 수 있습니다.");
		}
		status = CharacterStatus.SURVIVED;
	}

	public Long getId() {
		return id;
	}

	public Game getGame() {
		return game;
	}

	public Participant getParticipant() {
		return participant;
	}

	public String getTemplateType() {
		return templateType;
	}

	public String getOriginalPhotoUrl() {
		return originalPhotoUrl;
	}

	public String getCharacterImageUrl() {
		return characterImageUrl;
	}

	public String getPreviewImageUrl() {
		return previewImageUrl;
	}

	public Double getPositionX() {
		return positionX;
	}

	public Double getPositionY() {
		return positionY;
	}

	public Double getScale() {
		return scale;
	}

	public Double getRotation() {
		return rotation;
	}

	public String getQrToken() {
		return qrToken;
	}

	public CharacterStatus getStatus() {
		return status;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public Instant getPrintedAt() {
		return printedAt;
	}

	public Instant getFoundAt() {
		return foundAt;
	}

	public Participant getFoundByParticipant() {
		return foundByParticipant;
	}
}
