package com.smim.server.message;

import java.time.Instant;
import java.util.List;

/** nextCursor — 더 없으면 null */
public record MessagePage(List<MessageResponse> messages, Instant nextCursor) {
}
