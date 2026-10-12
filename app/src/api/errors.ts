/**
 * 서버 오류. 형태: { code, message } — docs/api.md 4장
 * mock 도 같은 오류를 던져 화면이 서버 유무와 무관하게 같은 분기를 타게 한다.
 */
export type ErrorCode =
  /** 서버에 닿지 못했다 — 오프라인, DNS, 시간 초과. 앱이 붙이는 코드다 */
  | 'NETWORK'
  | 'INVALID_DEVICE_KEY'
  | 'NOT_OWNER'
  | 'BANNED_AREA'
  | 'ALREADY_REPORTED'
  | 'RATE_LIMITED'
  | (string & {});

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: ErrorCode | null,
    message: string,
  ) {
    super(message);
  }

  /** 응답을 받지 못한 경우. 서버 오류(5xx)와 구분해야 안내 문구가 맞는다 */
  get isNetwork(): boolean {
    return this.code === 'NETWORK';
  }
}

export function networkError(): ApiError {
  return new ApiError(0, 'NETWORK', '서버에 연결할 수 없습니다.');
}
