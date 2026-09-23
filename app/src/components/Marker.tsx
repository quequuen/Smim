import Svg, { Circle, Path, Rect } from 'react-native-svg';
import { decodeMarker, type Shape } from '../lib/marker';
import { useTheme } from '../theme/useTheme';

/**
 * 익명 표식.
 *
 * 유니코드 문자(●▲■◆★⬢)로 그리지 않는다. 기기·OS 폰트에 따라 이모지로
 * 렌더링되거나(★), 글리프가 없거나(⬢), 크기·베이스라인이 제각각이 된다.
 * SVG 로 직접 그려야 기기와 무관하게 고정된다.
 */

const BOX = 16;

function ShapePath({ shape, color }: { shape: Shape; color: string }) {
  switch (shape) {
    case 'circle':
      return <Circle cx={8} cy={8} r={4.8} fill={color} />;
    case 'triangle':
      return <Path d="M8 2.8 L13.2 12.6 L2.8 12.6 Z" fill={color} />;
    case 'square':
      return <Rect x={3.6} y={3.6} width={8.8} height={8.8} rx={0.8} fill={color} />;
    case 'diamond':
      return <Path d="M8 2.4 L13.6 8 L8 13.6 L2.4 8 Z" fill={color} />;
    case 'star':
      return (
        <Path
          d="M8 2 L9.53 5.9 L13.71 6.15 L10.47 8.8 L11.53 12.85 L8 10.6 L4.47 12.85 L5.53 8.8 L2.29 6.15 L6.47 5.9 Z"
          fill={color}
        />
      );
    case 'hexagon':
      return <Path d="M8 2.4 L13.2 5.4 L13.2 10.6 L8 13.6 L2.8 10.6 L2.8 5.4 Z" fill={color} />;
  }
}

export function Marker({ marker, size = 12 }: { marker: number; size?: number }) {
  const { palette } = useTheme();
  const { shape, colorIndex } = decodeMarker(marker);
  const color = palette.markers[colorIndex];

  return (
    <Svg width={size} height={size} viewBox={`0 0 ${BOX} ${BOX}`}>
      <ShapePath shape={shape} color={color} />
    </Svg>
  );
}
