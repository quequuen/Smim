package com.smim.server.common;

/**
 * 위도·경도 한 점 (WGS84).
 *
 * JTS Point 를 쓰지 않는 이유 — x=경도, y=위도 순서라 뒤바뀌기 쉽고, Redis 구현(트랙 A)에는 JTS 가 필요 없다.
 * PostGIS 로 넘길 때만 ST_MakePoint(lon, lat) 로 바꾼다.
 */
public record GeoPoint(double lat, double lon) {
}
