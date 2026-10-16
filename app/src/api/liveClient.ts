import { Client } from '@stomp/stompjs';
import { getDeviceKey } from '../lib/deviceKey';
import type { LiveConnection, LiveOptions } from './client';
import type { Message } from './types';

/**
 * 실시간 수신 — STOMP over WebSocket. 근거: docs/api.md WebSocket /ws
 *
 * 받기만 한다. 글쓰기는 REST(POST /messages) 로 한다.
 * 재연결 · 하트비트는 stompjs 가 한다. Wi-Fi ↔ LTE 전환처럼 소켓이 조용히 죽으면
 * 하트비트가 끊긴 것으로 알아채고 다시 붙는다.
 */

const RECONNECT_MS = 3_000;
/** 서버와 같은 값 (WebSocketConfig.HEARTBEAT_MS) */
const HEARTBEAT_MS = 10_000;

export function createLiveConnection(baseUrl: string, opts: LiveOptions): LiveConnection {
  // http://192.168.0.10:8082 → ws://192.168.0.10:8082/ws , https → wss
  const brokerURL = baseUrl.replace(/^http/, 'ws') + '/ws';
  let everConnected = false;

  const client = new Client({
    brokerURL,
    reconnectDelay: RECONNECT_MS,
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    // React Native 의 WebSocket 은 텍스트 프레임 끝의 NULL 을 떼어 버리고, 보낼 때도 문제가 있다.
    // stompjs 문서의 React Native 설정 그대로다
    forceBinaryWSFrames: true,
    appendMissingNULLonIncoming: true,

    // 연결(재연결 포함)할 때마다 헤더를 새로 만든다 — 그사이 움직였을 수 있다
    beforeConnect: async () => {
      const at = opts.getCoords();
      client.connectHeaders = {
        'X-Device-Key': await getDeviceKey(),
        'session-id': opts.sessionId,
        ...(at && { lat: String(at.lat), lon: String(at.lon) }),
      };
      opts.onStatus('connecting');
    },

    onConnect: () => {
      client.subscribe('/user/queue/messages', (frame) => {
        try {
          opts.onMessage(JSON.parse(frame.body) as Message);
        } catch (e) {
          console.warn('[live] 받은 글을 읽지 못했다', e);
        }
      });
      opts.onStatus('live');
      if (everConnected) opts.onReconnected();
      everConnected = true;
    },

    onWebSocketClose: () => opts.onStatus('closed'),
    onStompError: (frame) => console.warn('[live]', frame.headers.message),
  });

  return {
    start() {
      client.activate();
    },
    stop() {
      // 다시 start 하면 "재연결" 로 본다 — 백그라운드에 있던 동안 놓친 글을 보충해야 한다
      void client.deactivate();
      opts.onStatus('closed');
    },
  };
}
