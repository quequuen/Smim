package com.smim.server.config;

/** GET /config 응답. 근거: docs/api.md 3장 */
public record RuntimeConfig(String group, int radiusM, int keepRadiusM, int moveThresholdM, int heartbeatSec,
		int markerWindowHour) {
}
