package com.smim.server.common;

import org.springframework.http.HttpStatus;

/** 오류 코드. 근거: docs/api.md 4장 */
public enum ErrorCode {
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
	INVALID_DEVICE_KEY(HttpStatus.UNAUTHORIZED, "기기 키가 없거나 형식이 올바르지 않습니다."),
	NOT_OWNER(HttpStatus.FORBIDDEN, "내가 쓴 글만 지울 수 있습니다."),
	BANNED_AREA(HttpStatus.FORBIDDEN, "이 구역에는 글을 남길 수 없습니다."),
	// 지운 글·남의 차단 항목도 같은 404 — 존재 여부를 구분해 알려주지 않는다
	NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다."),
	ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 글입니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}
}
