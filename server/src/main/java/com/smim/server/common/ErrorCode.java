package com.smim.server.common;

import org.springframework.http.HttpStatus;

/** 오류 코드. 근거: docs/api.md 4장 */
public enum ErrorCode {
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
	INVALID_DEVICE_KEY(HttpStatus.UNAUTHORIZED, "기기 키가 없거나 형식이 올바르지 않습니다.");

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
