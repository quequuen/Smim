package com.smim.server.block;

import jakarta.validation.constraints.NotNull;

/** author_key 를 노출하지 않으므로 메시지로 작성자를 지목한다 (D4) */
public record BlockRequest(@NotNull Long messageId) {
}
