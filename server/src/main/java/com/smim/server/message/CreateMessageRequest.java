package com.smim.server.message;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 500 은 message.content VARCHAR(500) · 앱 CONTENT_MAX 와 맞춘다 */
public record CreateMessageRequest(@NotBlank @Size(max = 500) String content,
		@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double lon, Long replyToId) {
}
