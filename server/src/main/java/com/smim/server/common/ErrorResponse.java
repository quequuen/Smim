package com.smim.server.common;

/** { "code": "BANNED_AREA", "message": "..." } */
public record ErrorResponse(String code, String message) {

	static ErrorResponse of(ErrorCode code) {
		return new ErrorResponse(code.name(), code.message());
	}
}
