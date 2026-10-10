import { ApiError } from '../api';

/**
 * 오류를 사용자에게 보여줄 문장으로 바꾼다.
 * 서버가 준 message 를 그대로 보여주지 않는다 — 문체를 앱에서 한 곳으로 맞춘다.
 */
export function describeError(e: unknown, action: string): string {
  if (e instanceof ApiError) {
    if (e.isNetwork) return `연결할 수 없어서 ${action} 못했어요. 연결을 확인해 주세요.`;
    switch (e.code) {
      case 'BANNED_AREA':
        return '이 구역에는 글을 남길 수 없어요.';
      case 'RATE_LIMITED':
        return '너무 자주 남기고 있어요. 잠시 후 다시 시도해 주세요.';
      case 'NOT_OWNER':
        return '내가 쓴 글만 지울 수 있어요.';
      case 'ALREADY_REPORTED':
        return '이미 신고한 글이에요.';
    }
  }
  return `${action} 못했어요. 잠시 후 다시 시도해 주세요.`;
}
