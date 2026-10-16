import { randomUUID } from 'expo-crypto';

/**
 * 이번 실행의 접속 세션. 하트비트(POST /presence)와 실시간 연결(/ws)이 같은 값을 써야
 * 서버가 "이 위치의 이 사람에게 보낼 연결"을 찾을 수 있다.
 *
 * 앱을 켤 때마다 새로 만든다. 기기 키와 달리 저장하지 않는다.
 */
export const SESSION_ID = randomUUID();
