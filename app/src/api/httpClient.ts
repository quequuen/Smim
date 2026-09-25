import { getDeviceKey } from '../lib/deviceKey';
import type { ApiClient } from './client';
import type { MessagePage, RuntimeConfig } from './types';

/**
 * 실제 서버 호출. 근거: docs/api.md
 *
 * baseUrl 에 localhost 를 쓰면 안 된다 — 폰 입장에서 localhost 는 폰 자신이다.
 * 개발 중에는 맥의 LAN IP (`ipconfig getifaddr en0`) 를 쓴다.
 */

const TIMEOUT_MS = 8_000;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    message: string,
  ) {
    super(message);
  }
}

export function createHttpClient(baseUrl: string): ApiClient {
  const root = baseUrl.replace(/\/+$/, '');

  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const res = await fetch(root + path, {
        method,
        headers: {
          'X-Device-Key': await getDeviceKey(),
          ...(body !== undefined && { 'Content-Type': 'application/json' }),
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });

      if (!res.ok) {
        // 오류 형태: { code, message } — docs/api.md 4장
        const err = await res.json().catch(() => null);
        throw new ApiError(res.status, err?.code ?? null, err?.message ?? `${method} ${path} → ${res.status}`);
      }
      return (res.status === 204 ? undefined : await res.json()) as T;
    } finally {
      clearTimeout(timer);
    }
  }

  return {
    getConfig() {
      return request<RuntimeConfig>('GET', '/config');
    },

    getMessages({ at, radiusM, cursor }) {
      const q = new URLSearchParams({ lat: String(at.lat), lon: String(at.lon), radius: String(radiusM) });
      if (cursor) q.set('cursor', cursor);
      return request<MessagePage>('GET', `/messages?${q}`);
    },

    async postMessage({ at, content, replyToId }) {
      await request('POST', '/messages', { content, lat: at.lat, lon: at.lon, replyToId });
    },

    sendPresence({ at, sessionId }) {
      return request<void>('POST', '/presence', { lat: at.lat, lon: at.lon, sessionId });
    },
  };
}
