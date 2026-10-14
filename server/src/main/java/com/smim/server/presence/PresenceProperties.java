package com.smim.server.presence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** application.yml 의 smim.presence.* — 값과 근거는 그쪽 주석에 있다 */
@Validated
@ConfigurationProperties("smim.presence")
public record PresenceProperties(@NotBlank String store, @NotNull Duration staleAfter, @NotNull Duration purgeAfter,
		@Positive int moveToleranceM) {
}
