import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * 기기에만 남기는 작은 값들. 서버에 두지 않는다 (D6 — 읽음 상태는 로컬).
 * 비밀이 아닌 값만 둔다. 기기 키는 SecureStore 에 있다 (lib/deviceKey.ts).
 */
export const KEYS = {
  /** 답글 목록을 마지막으로 연 시각 (ISO) */
  repliesSeenAt: 'smim.repliesSeenAt',
  /** 동의한 이용약관 버전 */
  termsAgreed: 'smim.termsAgreed',
} as const;

export async function getItem(key: string): Promise<string | null> {
  try {
    return await AsyncStorage.getItem(key);
  } catch {
    return null;
  }
}

export async function setItem(key: string, value: string): Promise<void> {
  try {
    await AsyncStorage.setItem(key, value);
  } catch {
    // 저장에 실패해도 앱은 동작해야 한다 — 다음 실행 때 한 번 더 묻는 정도의 손해다
  }
}
