package com.hackathon.gdg.character.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CharacterSubmitRequest(
		@NotBlank @Size(max = 50) String templateType,
		@DecimalMin("0.0") @DecimalMax("1.0") Double positionX,
		@DecimalMin("0.0") @DecimalMax("1.0") Double positionY,
		@DecimalMin(value = "0.0", inclusive = false) Double scale,
		Double rotation
) {
}
