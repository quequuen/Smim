package com.smim.server.message;

import com.smim.server.common.ApiException;
import com.smim.server.common.ErrorCode;
import com.smim.server.common.GeoPoint;
import com.smim.server.config.SmimProperties;
import com.smim.server.config.SmimProperties.Moderation;
import com.smim.server.message.MessageRepository.Created;
import com.smim.server.message.MessageRepository.MarkerUse;
import com.smim.server.message.MessageResponse.QuotedMessage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 읽기 · 쓰기 · 지우기 · 신고. 근거: docs/api.md 3장
 *
 * 모더레이션(#16)은 두 갈래다.
 * - 신고한 사람에게 숨기기 — 임계치 없이 신고 즉시. MessageRepository.visibleTo() 가 한다
 * - 모두에게 가리기 · 구역 차단 — 임계치를 넘을 때. 여기 autoBlind() 가 한다
 */
@Service
public class MessageService {

	static final int MARKER_COUNT = 24;

	static final int REPLIES_LIMIT = 50;

	private static final Logger log = LoggerFactory.getLogger(MessageService.class);

	private final MessageRepository repo;

	private final SmimProperties props;

	private final ApplicationEventPublisher events;

	public MessageService(MessageRepository repo, SmimProperties props, ApplicationEventPublisher events) {
		this.repo = repo;
		this.props = props;
		this.events = events;
	}

	public MessagePage near(String me, MessageQuery q) {
		if (q.radius() > props.experiment().maxRadiusM()) {
			throw new ApiException(ErrorCode.INVALID_REQUEST);
		}
		int limit = q.limitOrDefault();
		// 하나 더 읽어 본다 — 마지막 페이지가 딱 limit 개일 때 빈 페이지를 한 번 더 부르지 않게
		List<MessageResponse> rows = repo.findNear(me, new GeoPoint(q.lat(), q.lon()), q.radius(), q.cursor(),
				limit + 1);
		if (rows.size() <= limit) {
			return new MessagePage(rows, null);
		}
		List<MessageResponse> page = rows.subList(0, limit);
		return new MessagePage(page, page.get(limit - 1).createdAt());
	}

	@Transactional
	public MessageResponse create(String me, CreateMessageRequest req) {
		// 도배 제한. 동시에 들어온 요청끼리는 약간 넘칠 수 있다 — 보안 경계가 아니라 제동장치라 그 정도는 괜찮다
		Moderation mod = props.moderation();
		if (repo.countRecentBy(me, mod.rateWindow()) >= mod.rateMax()) {
			throw new ApiException(ErrorCode.RATE_LIMITED);
		}

		GeoPoint at = new GeoPoint(req.lat(), req.lon());
		if (repo.inBannedArea(at)) {
			throw new ApiException(ErrorCode.BANNED_AREA);
		}
		QuotedMessage quote = req.replyToId() == null ? null
				: repo.findQuotable(me, req.replyToId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

		int marker = assignMarker(me, repo.recentMarkers(at, props.marker().areaM(), props.marker().windowHour()));
		Created c = repo.insert(me, req.content(), at, marker, req.replyToId());
		MessageResponse created = new MessageResponse(c.id(), req.content(), marker, true, c.createdAt(), quote);
		// 실시간 전달은 커밋 후에 (LiveDelivery)
		events.publishEvent(new MessageCreated(created, me, at));
		return created;
	}

	/**
	 * 표식 배정 (D4). 같은 구역 최근 window-hour 안에서
	 * 1. 내가 이미 쓴 글이 있으면 그 표식 — 같은 구간 안에서 같은 사람은 같은 기호여야 대화가 읽힌다
	 * 2. 아니면 아무도 안 쓴 가장 작은 번호 — 도형이 먼저 소진되고 색이 붙는다 (api.md 1-3)
	 * 3. 24개가 다 쓰였으면 무작위
	 *
	 * 동시에 두 요청이 같은 구역에 쓰면 같은 표식을 받을 수 있다 (D4 에 적어 둔 문제, devlog 05 에서 다룬다).
	 */
	static int assignMarker(String me, List<MarkerUse> recent) {
		Set<Integer> used = new HashSet<>();
		for (MarkerUse u : recent) {
			if (u.authorKey().equals(me)) {
				return u.marker();
			}
			used.add(u.marker());
		}
		for (int m = 0; m < MARKER_COUNT; m++) {
			if (!used.contains(m)) {
				return m;
			}
		}
		return ThreadLocalRandom.current().nextInt(MARKER_COUNT);
	}

	@Transactional
	public void delete(String me, long id) {
		MessageRepository.Ownership o = repo.lockOwnership(id)
			.filter(it -> !"deleted".equals(it.status()))
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
		if (!o.authorKey().equals(me)) {
			throw new ApiException(ErrorCode.NOT_OWNER);
		}
		repo.markDeleted(id);
	}

	/** 신고한 사람에게는 바로 보이지 않게 된다 — 목록 필터가 report 를 본다 */
	@Transactional
	public void report(String me, long id, String reason) {
		repo.findVisibleAuthor(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
		if (!repo.insertReport(id, me, reason)) {
			throw new ApiException(ErrorCode.ALREADY_REPORTED);
		}
		autoBlind(id);
	}

	/**
	 * 누적 신고가 임계치를 넘으면 모두에게 가린다. 1인 운영이라 수동 검토를 기다릴 수 없다.
	 *
	 * 한 기기는 UNIQUE (message_id, reporter_key) 로 한 번만 신고하므로 혼자서는 임계치를 못 채운다.
	 * 오탐이면 관리자 해제(POST /admin/messages/{id}/unhide)로 되돌린다.
	 */
	private void autoBlind(long id) {
		Moderation mod = props.moderation();
		if (repo.countReports(id) < mod.blindThreshold()) {
			return;
		}
		// 이미 가려져 있으면 false — 구역 검사를 두 번 하지 않기 위한 조건이기도 하다
		if (!repo.hide(id)) {
			return;
		}
		log.info("자동 블라인드 message={} (신고 {}건)", id, mod.blindThreshold());
		repo.locationOf(id).ifPresent(at -> autoBanArea(at, mod));
	}

	/**
	 * 한 자리에서 가려진 글이 거듭 나오면 그 구역 자체를 막는다.
	 *
	 * 세는 대상이 '신고' 가 아니라 '가려진 글' 인 것이 중요하다. 글 하나가 가려지려면 서로 다른 기기
	 * blind-threshold 대가 신고해야 하므로, 구역이 막히려면 사실상 그 곱만큼의 신고가 필요하다.
	 * 신고 몇 건으로 멀쩡한 장소가 통째로 막히는 일을 이것으로 막는다.
	 */
	private void autoBanArea(GeoPoint at, Moderation mod) {
		if (repo.inBannedArea(at)) {
			return; // 이미 막힌 자리
		}
		int hidden = repo.countHiddenNear(at, mod.areaRadiusM(), mod.areaWindow());
		if (hidden < mod.areaThreshold()) {
			return;
		}
		long areaId = repo.insertBannedArea(at, mod.areaRadiusM(), "auto: 신고 누적", "auto");
		log.warn("자동 구역 차단 area={} 반경 {}m (가려진 글 {}건)", areaId, mod.areaRadiusM(), hidden);
	}

	// ── 관리자 ────────────────────────────────────────────

	/** 관리자 숨김. 이미 가려졌거나 없는 글이면 404 — 상태를 구분해 알려주지 않는다 */
	public void hide(long id) {
		if (!repo.hide(id)) {
			throw new ApiException(ErrorCode.NOT_FOUND);
		}
		log.info("관리자 숨김 message={}", id);
	}

	/** 관리자 해제. 자동 블라인드 오탐을 되돌린다. 지운 글은 되살리지 않는다 */
	public void unhide(long id) {
		if (!repo.unhide(id)) {
			throw new ApiException(ErrorCode.NOT_FOUND);
		}
		log.info("관리자 해제 message={}", id);
	}

	public List<MessageRepository.ReportedMessage> reported(int limit) {
		return repo.findReported(limit);
	}

	public List<MessageRepository.BannedArea> bannedAreas() {
		return repo.listBannedAreas();
	}

	/** 수동 등록. 자동 등록과 구분하려고 source 를 'manual' 로 박는다 */
	public long addBannedArea(GeoPoint at, int radiusM, String reason) {
		long id = repo.insertBannedArea(at, radiusM, reason, "manual");
		log.info("금지 구역 등록 area={} 반경 {}m", id, radiusM);
		return id;
	}

	public void removeBannedArea(long id) {
		if (!repo.deleteBannedArea(id)) {
			throw new ApiException(ErrorCode.NOT_FOUND);
		}
		log.info("금지 구역 해제 area={}", id);
	}

	public List<ReplyResponse> replies(String me) {
		return repo.findRepliesTo(me, REPLIES_LIMIT);
	}
}
