package com.smim.server.block;

import java.time.Instant;

/** 차단 목록의 한 줄. messageContent — 지목한 글이 지워졌거나 숨겨졌으면 null */
public record BlockResponse(long id, String messageContent, Instant createdAt) {
}
