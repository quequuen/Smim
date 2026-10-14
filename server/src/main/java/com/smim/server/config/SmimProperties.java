package com.smim.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** application.yml 의 smim.* — 값과 근거는 그쪽 주석에 있다 */
@Validated
@ConfigurationProperties("smim")
public record SmimProperties(@NotNull @Valid Experiment experiment, @NotNull @Valid Location location,
		@NotNull @Valid Marker marker) {

	public record Experiment(@NotEmpty List<@Valid Group> groups) {

		/** GET /messages 가 받아들이는 반경 상한. 이보다 넓게 긁어 가지 못하게 한다 */
		public int maxRadiusM() {
			return groups.stream().mapToInt(Group::radiusM).max().orElseThrow();
		}
	}

	public record Group(@NotBlank String name, @Positive int radiusM, @Positive int keepRadiusM,
			@Positive int moveThresholdM) {
	}

	public record Location(@Positive int heartbeatSec) {
	}

	public record Marker(@Positive int windowHour, @Positive int areaM) {
	}
}
