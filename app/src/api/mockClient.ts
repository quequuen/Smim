import type { ApiClient } from './client';
import { ApiError } from './errors';
import { DEFAULT_CONFIG, type Block, type Message, type MessagePage, type QuotedMessage } from './types';

/**
 * 서버가 생기기 전까지 쓰는 mock. **메모리에 글을 들고 있어서** 쓰면 목록에 붙는다.
 * 앱을 다시 켜면 처음 상태로 돌아간다.
 *
 * 시간 구분선 규칙이 검증되도록 만들었다 — 3블록, 그중 하나는 3개월 이상 벌어진다.
 * 오래된 날짜는 계절 맥락(벚꽃·눈·단풍)이 맞도록 고정했고,
 * 최근 블록만 "어제"가 나오도록 실행 시점 기준으로 만든다.
 */

/** 빈 화면을 보려면 이 값을 true 로 바꾼다 */
export const MOCK_EMPTY = false;

function yesterdayAt(hour: number, minute: number): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
}

const SNOW = {
  id: 1280,
  content: '벚꽃 얘기 보고 왔는데 지금은 눈 옴 ㅋㅋㅋ',
  createdAt: '2025-12-03T09:11:00+09:00',
};

const SEED: Message[] = [
  // ── 2025년 4월 · 벚꽃 ──
  { id: 1201, content: '여기 벚꽃 미쳤다', marker: 0, isMine: false, createdAt: '2025-04-12T14:23:00+09:00', replyTo: null },
  { id: 1202, content: 'ㄹㅇ 지금이 절정인 듯', marker: 7, isMine: false, createdAt: '2025-04-12T14:41:00+09:00', replyTo: null },
  { id: 1203, content: '3번 출구 쪽이 더 예뻐요', marker: 0, isMine: false, createdAt: '2025-04-12T15:02:00+09:00', replyTo: null },

  // ── 2025년 12월 · 눈 (8개월 벌어짐 → 넓은 여백) ──
  { id: SNOW.id, content: SNOW.content, marker: 0, isMine: false, createdAt: SNOW.createdAt, replyTo: null },

  // ── 어제 · 단풍 ──
  { id: 1290, content: '저도 그거 보고 왔는데 지금은 단풍 들었어요', marker: 0, isMine: false, createdAt: yesterdayAt(11, 5), replyTo: SNOW },
  { id: 1291, content: '여기 지금 사람 많아요?', marker: 14, isMine: false, createdAt: yesterdayAt(17, 30), replyTo: null },
  { id: 1292, content: '1년 전 사람들 아직 여기 오나', marker: 2, isMine: true, createdAt: yesterdayAt(21, 12), replyTo: null },
];

/**
 * 시드 글의 작성자. 화면에는 절대 나가지 않는 서버 내부 값(author_key) 역할이다.
 * 1201·1203 은 같은 사람 — 한 명을 차단하면 두 글이 함께 사라지는지 확인할 수 있다.
 */
const ME = 'me';
const SEED_AUTHORS: Record<number, string> = {
  1201: 'a', 1202: 'b', 1203: 'a', 1280: 'c', 1290: 'd', 1291: 'e', 1292: ME,
};

type Row = { message: Message; author: string; status: 'visible' | 'deleted' };

/** 서버의 message · report · user_block 테이블 역할 */
const store = {
  rows: (MOCK_EMPTY ? [] : SEED).map<Row>((m) => ({ message: m, author: SEED_AUTHORS[m.id], status: 'visible' })),
  reported: new Set<number>(),
  blocks: [] as (Block & { author: string })[],
  nextId: 2000,
};

function findRow(id: number): Row {
  const row = store.rows.find((r) => r.message.id === id && r.status === 'visible');
  if (!row) throw new ApiError(404, null, '글을 찾을 수 없습니다.');
  return row;
}

/** 이 사용자에게 보이는가 — 지운 글, 신고한 글, 차단한 작성자의 글은 빠진다 */
function isVisible(r: Row): boolean {
  return (
    r.status === 'visible' &&
    !store.reported.has(r.message.id) &&
    !store.blocks.some((b) => b.author === r.author)
  );
}

/** 인용한 원글이 더는 보이지 않으면 내용을 비운다 */
function withQuote(m: Message): Message {
  if (!m.replyTo) return m;
  const original = store.rows.find((r) => r.message.id === m.replyTo!.id);
  return original && isVisible(original) ? m : { ...m, replyTo: { ...m.replyTo, content: null } };
}

function delay<T>(value: T, ms = 320): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

function quote(m: Message): QuotedMessage {
  return { id: m.id, content: m.content, createdAt: m.createdAt };
}

/**
 * 표식 배정 흉내. 서버는 같은 구역 최근 markerWindowHour 안에 쓰인 표식을 피한다 (D4).
 * mock 은 구역 구분 없이 시간 조건만 본다.
 */
function pickMarker(now: number): number {
  const windowMs = DEFAULT_CONFIG.markerWindowHour * 60 * 60 * 1000;
  const used = new Set(
    store.rows
      .map((r) => r.message)
      .filter((m) => now - new Date(m.createdAt).getTime() < windowMs)
      .map((m) => m.marker),
  );
  for (let marker = 0; marker < 24; marker += 1) {
    if (!used.has(marker)) return marker;
  }
  return Math.floor(Math.random() * 24);
}

export const mockClient: ApiClient = {
  async getConfig() {
    return delay(DEFAULT_CONFIG, 120);
  },

  async getMessages(): Promise<MessagePage> {
    // 서버는 최신순으로 내려준다 (createdAt DESC)
    const sorted = store.rows
      .filter(isVisible)
      .map((r) => withQuote(r.message))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return delay({ messages: sorted, nextCursor: null });
  },

  async postMessage({ content, replyToId }) {
    const now = Date.now();
    const target = replyToId != null ? findRow(replyToId).message : undefined;
    const created: Message = {
      id: store.nextId++,
      content,
      marker: pickMarker(now),
      isMine: true,
      createdAt: new Date(now).toISOString(),
      replyTo: target ? quote(target) : null,
    };
    store.rows.push({ message: created, author: ME, status: 'visible' });
    return delay(created);
  },

  async deleteMessage(id) {
    const row = findRow(id);
    if (row.author !== ME) throw new ApiError(403, 'NOT_OWNER', '내가 쓴 글만 지울 수 있습니다.');
    row.status = 'deleted';
    return delay(undefined);
  },

  async reportMessage(id) {
    findRow(id);
    if (store.reported.has(id)) throw new ApiError(409, 'ALREADY_REPORTED', '이미 신고한 글입니다.');
    store.reported.add(id);
    return delay(undefined);
  },

  async blockAuthor(messageId) {
    const row = findRow(messageId);
    store.blocks.push({
      id: store.nextId++,
      author: row.author,
      messageContent: row.message.content,
      createdAt: new Date().toISOString(),
    });
    return delay(undefined);
  },

  async getBlocks() {
    // author 는 서버 내부 값이므로 응답에서 뺀다
    return delay(store.blocks.map(({ author: _author, ...b }) => b).reverse());
  },

  async unblock(blockId) {
    store.blocks = store.blocks.filter((b) => b.id !== blockId);
    return delay(undefined);
  },

  async sendPresence() {
    return delay(undefined, 80);
  },
};
