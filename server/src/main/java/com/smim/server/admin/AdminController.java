package com.smim.server.admin;

import com.smim.server.common.GeoPoint;
import com.smim.server.message.MessageRepository;
import com.smim.server.message.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 API. 근거: docs/api.md 5장 · #16
 *
 * 모든 메서드가 먼저 auth.verify() 를 부른다. 인터셉터로 묶지 않은 것은 의도적이다 —
 * 경로가 여섯 개뿐이고, 설정 파일을 봐야 보호 여부를 알 수 있는 것보다 코드에 보이는 편이 낫다.
 *
 * 일반 API 와 달리 응답에 좌표와 글 내용이 그대로 나간다. 검토하려면 봐야 하기 때문이다.
 */
@Validated
@RestController
@RequestMapping("/admin")
public class AdminController {

	private static final int REPORTS_LIMIT_DEFAULT = 50;

	private final AdminAuth auth;

	private final MessageService service;

	public AdminController(AdminAuth auth, MessageService service) {
		this.auth = auth;
		this.service = service;
	}

	/** 검토 대기열 — 신고가 붙은 글을 많이 신고된 순으로 */
	@GetMapping("/reports")
	public Map<String, List<MessageRepository.ReportedMessage>> reports(
			@RequestHeader(name = AdminAuth.HEADER, required = false) String token,
			@RequestParam(defaultValue = "" + REPORTS_LIMIT_DEFAULT) @Positive @Max(200) int limit) {
		auth.verify(token);
		return Map.of("reports", service.reported(limit));
	}

	@PostMapping("/messages/{id}/hide")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void hide(@RequestHeader(name = AdminAuth.HEADER, required = false) String token,
			@PathVariable long id) {
		auth.verify(token);
		service.hide(id);
	}

	/** 자동 블라인드 오탐을 되돌린다 */
	@PostMapping("/messages/{id}/unhide")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unhide(@RequestHeader(name = AdminAuth.HEADER, required = false) String token,
			@PathVariable long id) {
		auth.verify(token);
		service.unhide(id);
	}

	@GetMapping("/banned-areas")
	public Map<String, List<MessageRepository.BannedArea>> bannedAreas(
			@RequestHeader(name = AdminAuth.HEADER, required = false) String token) {
		auth.verify(token);
		return Map.of("areas", service.bannedAreas());
	}

	@PostMapping("/banned-areas")
	@ResponseStatus(HttpStatus.CREATED)
	public Map<String, Long> addBannedArea(
			@RequestHeader(name = AdminAuth.HEADER, required = false) String token,
			@Valid @RequestBody BannedAreaRequest req) {
		auth.verify(token);
		return Map.of("id", service.addBannedArea(new GeoPoint(req.lat(), req.lon()), req.radiusM(), req.reason()));
	}

	@DeleteMapping("/banned-areas/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeBannedArea(@RequestHeader(name = AdminAuth.HEADER, required = false) String token,
			@PathVariable long id) {
		auth.verify(token);
		service.removeBannedArea(id);
	}
}
