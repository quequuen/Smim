package com.smim.server.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** reason 64 는 banned_area.reason VARCHAR(64) 와 맞춘다 */
public record BannedAreaRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
		// 상한을 둔다. 0 을 하나 더 붙이면 도시 하나가 통째로 막힌다
		@NotNull @Positive @Max(50_000) Integer radiusM, @NotBlank @Size(max = 64) String reason) {
}
