import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Message } from '../api';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';
import { Marker } from './Marker';

/**
 * 내 글에는 표식을 붙이지 않고 오른쪽 정렬로만 구분한다.
 * 남은 구분되지 않고 나만 구분된다 (D4).
 */
export function MessageRow({ message, onLongPress }: { message: Message; onLongPress?: (m: Message) => void }) {
  const { palette } = useTheme();

  return (
    <Pressable
      onLongPress={onLongPress && (() => onLongPress(message))}
      delayLongPress={350}
      style={({ pressed }) => (pressed && onLongPress ? { backgroundColor: palette.ruleSoft } : null)}
    >
      {message.isMine ? <Mine message={message} /> : <Others message={message} />}
    </Pressable>
  );
}

function Mine({ message }: { message: Message }) {
  const { palette } = useTheme();
  return (
    <View style={styles.mineWrap}>
      {message.replyTo && (
        <Text
          numberOfLines={2}
          style={[styles.quote, styles.mineQuote, { color: palette.muted, borderRightColor: palette.rule }]}
        >
          {message.replyTo.content}
        </Text>
      )}
      <Text style={[styles.body, styles.mineBody, { color: palette.ink }]}>{message.content}</Text>
    </View>
  );
}

function Others({ message }: { message: Message }) {
  const { palette } = useTheme();
  return (
    <View style={styles.row}>
      <View style={styles.markerSlot}>
        <Marker marker={message.marker} />
      </View>
      <View style={styles.bodyCol}>
        {message.replyTo && (
          <Text numberOfLines={2} style={[styles.quote, { color: palette.muted, borderLeftColor: palette.rule }]}>
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
    gap: 4,
  },
  // 내 글은 오른쪽 정렬이라 인용선도 오른쪽에 둔다
  mineQuote: {
    borderLeftWidth: 0,
    paddingLeft: 0,
    borderRightWidth: 2,
    paddingRight: 9,
    textAlign: 'right',
  },
  mineBody: {
    fontFamily: font.sansMedium,
    textAlign: 'right',
  },
});
