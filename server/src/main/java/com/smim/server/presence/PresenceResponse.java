package com.smim.server.presence;

/**
 * 하트비트 응답. 근거: docs/api.md 3장
 *
 * @param nearby 반경 안에 지금 있는 사람 수(나 포함). <b>하한에 못 미치면 null</b> 이다 —
 * 서버에서 지우고 보낸다. 앱이 숨기게 하면 응답에 값이 남아 있고, 앱 한 줄만 고쳐도 드러난다.
 */
public record PresenceResponse(Integer nearby) {

	static final PresenceResponse HIDDEN = new PresenceResponse(null);
}
