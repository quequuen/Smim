package com.smim.server.admin;

import com.smim.server.common.ApiException;
import com.smim.server.common.ErrorCode;
import com.smim.server.config.SmimProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * /admin/** 의 관문. 근거: docs/api.md 5장
 *
 * 기기 키(X-Device-Key)와는 전혀 다른 체계다. 기기 키는 클라이언트가 스스로 만드는 값이라
 * 신원을 증명하지 못한다 — 누가 썼는지 구분할 뿐이다. 관리자는 서버만 아는 비밀이어야 한다.
 */
@Component
public class AdminAuth {

	public static final String HEADER = "X-Admin-Token";

	private final SmimProperties props;

	public AdminAuth(SmimProperties props) {
		this.props = props;
	}

	/** 틀리면 NOT_ADMIN(401). 토큰이 설정돼 있지 않으면 무엇을 보내도 통과하지 못한다 */
	public void verify(String header) {
		SmimProperties.Admin admin = props.admin();
		if (!admin.configured() || header == null) {
			throw new ApiException(ErrorCode.NOT_ADMIN);
		}
		// equals 는 다른 첫 글자에서 바로 끝나 비교 시간이 샌다. 길이와 내용을 상수 시간으로 본다
		byte[] expected = admin.token().getBytes(StandardCharsets.UTF_8);
		byte[] actual = header.getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, actual)) {
			throw new ApiException(ErrorCode.NOT_ADMIN);
		}
	}
}
