/**
 * 서버 오류. 형태: { code, message } — docs/api.md 4장
 * mock 도 같은 오류를 던져 화면이 서버 유무와 무관하게 같은 분기를 타게 한다.
 */
export type ErrorCode =
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
}
