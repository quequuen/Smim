/**
 * 익명 표식 (D4). 서버는 0~23 숫자만 내려주고 도형·색 매핑은 앱이 한다.
 *
 *   도형 = marker % 6
 *   색   = marker / 6
 *
 * 순서대로 배정하면 도형이 먼저 소진되고 그다음 색이 붙는다.
 * 색각 이상이 있어도 도형만으로 구분되어야 하므로 이 순서가 중요하다.
 */

export const SHAPES = ['circle', 'triangle', 'square', 'diamond', 'star', 'hexagon'] as const;
export type Shape = (typeof SHAPES)[number];

export const SHAPE_COUNT = SHAPES.length;
export const COLOR_COUNT = 4;
export const MARKER_MAX = SHAPE_COUNT * COLOR_COUNT; // 24

export function decodeMarker(marker: number): { shape: Shape; colorIndex: number } {
  const m = ((marker % MARKER_MAX) + MARKER_MAX) % MARKER_MAX;
  return {
    shape: SHAPES[m % SHAPE_COUNT],
    colorIndex: Math.floor(m / SHAPE_COUNT),
  };
}
