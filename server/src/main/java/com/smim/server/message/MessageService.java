package com.smim.server.message;

import com.smim.server.common.ApiException;
import com.smim.server.common.ErrorCode;
import com.smim.server.common.GeoPoint;
import com.smim.server.config.SmimProperties;
import com.smim.server.message.MessageRepository.Created;
import com.smim.server.message.MessageRepository.MarkerUse;
import com.smim.server.message.MessageResponse.QuotedMessage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 읽기 · 쓰기 · 지우기 · 신고. 근거: docs/api.md 3장
 *
 * 자동 블라인드 · 도배 제한 · 관리자 숨김은 #16 (7주차) 에서 붙인다.
 */
@Service
public class MessageService {

	static final int MARKER_COUNT = 24;

	static final int REPLIES_LIMIT = 50;

	private final MessageRepository repo;

	private final SmimProperties props;

	public MessageService(MessageRepository repo, SmimProperties props) {
		this.repo = repo;
		this.props = props;
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
		GeoPoint at = new GeoPoint(req.lat(), req.lon());
		if (repo.inBannedArea(at)) {
			throw new ApiException(ErrorCode.BANNED_AREA);
		}
		QuotedMessage quote = req.replyToId() == null ? null
				: repo.findQuotable(me, req.replyToId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

		int marker = assignMarker(me, repo.recentMarkers(at, props.marker().areaM(), props.marker().windowHour()));
		Created c = repo.insert(me, req.content(), at, marker, req.replyToId());
		return new MessageResponse(c.id(), req.content(), marker, true, c.createdAt(), quote);
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
	public void report(String me, long id, String reason) {
		repo.findVisibleAuthor(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
		if (!repo.insertReport(id, me, reason)) {
			throw new ApiException(ErrorCode.ALREADY_REPORTED);
		}
	}

	public List<ReplyResponse> replies(String me) {
		return repo.findRepliesTo(me, REPLIES_LIMIT);
	}
}
