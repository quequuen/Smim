import { StyleSheet, Text, View } from 'react-native';
import type { Message } from '../api';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';
import { Marker } from './Marker';

/**
 * 내 글에는 표식을 붙이지 않고 오른쪽 정렬로만 구분한다.
 * 남은 구분되지 않고 나만 구분된다 (D4).
 */
export function MessageRow({ message }: { message: Message }) {
  const { palette } = useTheme();

  if (message.isMine) {
    return (
      <View style={styles.mineWrap}>
        <Text style={[styles.body, styles.mineBody, { color: palette.ink }]}>{message.content}</Text>
      </View>
    );
  }

  return (
    <View style={styles.row}>
      <View style={styles.markerSlot}>
        <Marker marker={message.marker} />
      </View>
      <View style={styles.bodyCol}>
        {message.replyTo && (
          <Text style={[styles.quote, { color: palette.muted, borderLeftColor: palette.rule }]}>
            {message.replyTo.content}
          </Text>
        )}
        <Text style={[styles.body, { color: palette.ink2 }]}>{message.content}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.markerGap,
    paddingHorizontal: spacing.screenX,
  },
  markerSlot: {
    width: 12,
    // 본문 첫 줄의 광학 중심에 맞춘다 (15px × 1.6 행간)
    paddingTop: 6,
  },
  bodyCol: {
    flex: 1,
    gap: 4,
  },
  body: {
    fontFamily: font.sans,
    fontSize: 15,
    lineHeight: 24,
  },
  quote: {
    fontFamily: font.sans,
    fontSize: 12.5,
    lineHeight: 18,
    borderLeftWidth: 2,
    paddingLeft: 9,
  },
  mineWrap: {
    paddingHorizontal: spacing.screenX,
    paddingLeft: spacing.screenX + 40,
    alignItems: 'flex-end',
  },
  mineBody: {
    fontFamily: font.sansMedium,
    textAlign: 'right',
  },
});
