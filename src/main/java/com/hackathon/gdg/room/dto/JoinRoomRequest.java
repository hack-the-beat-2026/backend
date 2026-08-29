package com.hackathon.gdg.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(
		@NotBlank(message = "닉네임은 필수입니다.")
		@Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
		String nickname
) {
}
