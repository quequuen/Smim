package com.smim.server.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * X-Device-Key 헤더 → author_key. 근거: docs/api.md 2장
 *
 * 원본 키는 저장하지 않는다. SHA-256 hex(64자) 만 author_key 로 쓴다.
 */
public final class DeviceKeys {

	public static final String HEADER = "X-Device-Key";

	private static final int KEY_BYTES = 32;

	private DeviceKeys() {
	}

	public static String toAuthorKey(String header) {
		if (header == null || !isValid(header)) {
			throw new ApiException(ErrorCode.INVALID_DEVICE_KEY);
		}
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(header.getBytes(StandardCharsets.US_ASCII));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean isValid(String header) {
		try {
			return Base64.getDecoder().decode(header).length == KEY_BYTES;
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}
}
