/**
 * 색·타이포·간격 토큰. 근거: docs/ui-spec.md
 *
 * 채도 있는 색은 표식(marker)에만 쓴다. 본문·버튼·구분선이 전부 무채색이라
 * 표식이 기능적으로 먼저 눈에 들어온다.
 */

export type Palette = {
  paper: string;
  surface: string;
  ink: string;
  ink2: string;
  muted: string;
  rule: string;
  ruleSoft: string;
  /** 표식 4색 — 틸 · 클레이 · 앰버 · 슬레이트 */
  markers: [string, string, string, string];
};

export const lightPalette: Palette = {
  paper: '#F7F5F0',
  surface: '#FFFFFF',
  ink: '#1A1814',
  ink2: '#4A453D',
  muted: '#6E675C',
  rule: '#E2DDD2',
  ruleSoft: '#EDE9E1',
  markers: ['#2A7268', '#9E4E2C', '#806115', '#5A6B7A'],
};

export const darkPalette: Palette = {
  paper: '#14120F',
  surface: '#1C1915',
  ink: '#EDE8DF',
  ink2: '#C5BDB0',
  muted: '#968D80',
  rule: '#2F2A23',
  ruleSoft: '#241F1A',
  markers: ['#5FC3B4', '#E0916A', '#D4B155', '#8DA3B5'],
};

/** 번들된 폰트 패밀리 이름 (App.tsx 에서 로드) */
export const font = {
  sans: 'GothicA1_400Regular',
  sansMedium: 'GothicA1_500Medium',
  sansSemiBold: 'GothicA1_600SemiBold',
  mono: 'IBMPlexMono_400Regular',
  monoMedium: 'IBMPlexMono_500Medium',
} as const;

/**
 * RN 의 letterSpacing 은 px 단위다. 시안의 em 값에 폰트 크기를 곱해 넣는다.
 */
export const spacing = {
  screenX: 20,
  headerHeight: 56,
  messageGap: 11,
  markerGap: 10,
  /** 구분선 위 여백 — 기본 */
  sepTop: 14,
  /** 구분선 위 여백 — 직전 블록과 3개월 이상 벌어졌을 때.
   *  색이 아니라 여백으로 표현한다. 시간 거리를 공간 거리로. */
  sepTopWide: 56,
  sepBottom: 14,
  touchTarget: 44,
} as const;
