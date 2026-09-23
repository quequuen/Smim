/**
 * 시간 표기 (docs/ui-spec.md §1)
 *
 *   날짜가 주 라벨, 현재 기준 경과가 보조.
 *
 * "8개월 후" 처럼 직전 블록을 기준으로 삼지 않는다. 채팅 스크롤은 위로 올릴수록
 * 과거라 "N 후"는 방향이 거꾸로이고, 앞 라벨을 알아야 해석되기 때문이다.
 *
 * 경과 표기는 서버가 만들지 않는다. 앱이 백그라운드에 오래 머물면 값이 굳는다.
 * 반드시 렌더링 시점에 계산한다.
 */

const DAY_MS = 24 * 60 * 60 * 1000;

/** 그 날의 자정 기준으로 며칠 차이인지 */
function daysApart(a: Date, b: Date): number {
  const da = new Date(a.getFullYear(), a.getMonth(), a.getDate()).getTime();
  const db = new Date(b.getFullYear(), b.getMonth(), b.getDate()).getTime();
  return Math.round((db - da) / DAY_MS);
}

function monthsApart(a: Date, b: Date): number {
  return (b.getFullYear() - a.getFullYear()) * 12 + (b.getMonth() - a.getMonth());
}

/** 주 라벨 — 항상 `YYYY년 M월 D일` */
export function formatDate(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일`;
}

/** 보조 라벨 — 현재 기준 경과 */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const d = new Date(iso);
  const days = daysApart(d, now);

  if (days <= 0) return '오늘';
  if (days === 1) return '어제';
  if (days < 7) return `${days}일 전`;

  const months = monthsApart(d, now) - (now.getDate() < d.getDate() ? 1 : 0);
  if (months < 1) return `${Math.floor(days / 7)}주 전`;
  if (months < 12) return `${months}개월 전`;

  const years = Math.floor(months / 12);
  const rest = months % 12;
  return rest === 0 ? `${years}년 전` : `${years}년 ${rest}개월 전`;
}

/** 날짜 블록 사이가 이만큼 벌어지면 구분선 위 여백을 넓힌다 */
export const WIDE_GAP_MONTHS = 3;

export function isWideGap(prevIso: string, currIso: string): boolean {
  return monthsApart(new Date(prevIso), new Date(currIso)) >= WIDE_GAP_MONTHS;
}

/** `YYYY-MM-DD` — 같은 날끼리 묶는 키 */
export function dayKey(iso: string): string {
  const d = new Date(iso);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}
