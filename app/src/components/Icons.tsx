import Svg, { Circle, Path } from 'react-native-svg';

type IconProps = { color: string; size?: number };

export function ReplyIcon({ color, size = 20 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 20 20" fill="none">
      <Path
        d="M17 9.5c0 3.3-3.1 6-7 6-.9 0-1.8-.15-2.6-.4L3 16.5l1.5-3.3C3.55 12.2 3 10.9 3 9.5c0-3.3 3.1-6 7-6s7 2.7 7 6Z"
        stroke={color}
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}

export function MenuIcon({ color, size = 20 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 20 20" fill="none">
      <Path d="M3 6h14M3 10h14M3 14h9" stroke={color} strokeWidth={1.5} strokeLinecap="round" />
    </Svg>
  );
}

export function SendIcon({ color, size = 18 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 18 18" fill="none">
      <Path
        d="M9 14.5V3.5M4 8.5l5-5 5 5"
        stroke={color}
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}

/** 빈 화면의 동심원 — "이 자리"를 뜻하는 최소한의 표시 */
export function PlaceMarkIcon({ color, dot, size = 66 }: IconProps & { dot: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 66 66" fill="none">
      <Circle cx={33} cy={33} r={31} stroke={color} strokeWidth={1} strokeDasharray="3 7" />
      <Circle cx={33} cy={33} r={17} stroke={color} strokeWidth={1} strokeDasharray="3 7" />
      <Circle cx={33} cy={33} r={3.5} fill={dot} />
    </Svg>
  );
}
