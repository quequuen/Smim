import { StyleSheet, Text, View } from 'react-native';
import { formatDate, formatRelative } from '../lib/time';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';

/**
 * 이 앱의 시그니처 UI.
 *
 * 날짜가 주 라벨, 현재 기준 경과가 보조. 둘 다 가운데 정렬해 하나의 도장처럼 읽히게 한다.
 * 직전 블록과 3개월 이상 벌어지면 색이 아니라 **위 여백**을 넓힌다 —
 * 색은 "중요하다"를 뜻하지만 여기서 전하려는 건 "멀다"이기 때문이다.
 */
export function TimeSeparator({ iso, wideGap }: { iso: string; wideGap: boolean }) {
  const { palette } = useTheme();

  return (
    <View
      style={[
        styles.wrap,
        {
          backgroundColor: palette.paper,
          paddingTop: wideGap ? spacing.sepTopWide : spacing.sepTop,
          paddingBottom: spacing.sepBottom,
        },
      ]}
    >
      <View style={styles.line}>
        <View style={[styles.rule, { backgroundColor: palette.rule }]} />
        <Text style={[styles.date, { color: palette.ink2 }]}>{formatDate(iso)}</Text>
        <View style={[styles.rule, { backgroundColor: palette.rule }]} />
      </View>
      <Text style={[styles.relative, { color: palette.muted }]}>{formatRelative(iso)}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: spacing.screenX,
  },
  line: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    alignSelf: 'stretch',
  },
  rule: {
    flexGrow: 1,
    height: StyleSheet.hairlineWidth,
  },
  date: {
    fontFamily: font.mono,
    fontSize: 11,
    letterSpacing: 1.1, // 0.1em × 11px — RN 의 letterSpacing 은 px 단위다
  },
  relative: {
    fontFamily: font.mono,
    fontSize: 10,
    letterSpacing: 1.0,
  },
});
