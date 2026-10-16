package com.smim.server.message;

import com.smim.server.common.GeoPoint;

/** 저장이 커밋된 뒤 실시간 전달(LiveDelivery)이 받는다. authorKey · 좌표는 서버 안에서만 쓴다 */
public record MessageCreated(MessageResponse message, String authorKey, GeoPoint at) {
}
