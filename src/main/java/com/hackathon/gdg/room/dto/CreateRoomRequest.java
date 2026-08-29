package com.hackathon.gdg.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
		@NotBlank(message = "방 이름은 필수입니다.")
		@Size(max = 100, message = "방 이름은 100자 이하여야 합니다.")
		String name,

		@Min(value = 1, message = "디자인 시간은 1초 이상이어야 합니다.")
		@Max(value = 86400, message = "디자인 시간은 86400초 이하여야 합니다.")
		int designDurationSeconds,

		@Min(value = 1, message = "숨기기 시간은 1초 이상이어야 합니다.")
		@Max(value = 86400, message = "숨기기 시간은 86400초 이하여야 합니다.")
		int hideDurationSeconds,

		@Min(value = 1, message = "탐색 시간은 1초 이상이어야 합니다.")
		@Max(value = 86400, message = "탐색 시간은 86400초 이하여야 합니다.")
		int seekDurationSeconds,

		@Min(value = 1, message = "SEEKER는 1명 이상이어야 합니다.")
		@Max(value = 100, message = "SEEKER는 100명 이하여야 합니다.")
		int seekerCount
) {
}
