import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';
import type { Reply } from '../api';
import { Marker } from '../components/Marker';
import { ScreenModal } from '../components/ScreenModal';
import { formatRelative, isAfter } from '../lib/time';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';

/**
 * 내 글에 달린 답글 (D6).
 *
 * 답글 내용은 여기서 바로 읽힌다. 다만 앞뒤 대화는 그 자리에 가야 보인다 —
 * "그 자리에 가야만 읽힌다"는 원칙과 피드백 사이의 절충이다.
 */
export function RepliesScreen({
  visible,
  onClose,
  replies,
  seenBefore,
}: {
  visible: boolean;
  onClose: () => void;
  replies: Reply[] | null;
  /** 이번에 열기 직전의 마지막 확인 시각. 이후에 온 답글에 점을 찍는다 */
  seenBefore: string | null;
}) {
  const { palette } = useTheme();

  return (
    <ScreenModal visible={visible} title="내 글에 달린 답글" onClose={onClose}>
      {replies === null ? (
        <View style={styles.center}>
          <ActivityIndicator color={palette.muted} />
        </View>
      ) : replies.length === 0 ? (
        <View style={styles.center}>
          <Text style={[styles.emptyTitle, { color: palette.ink }]}>아직 답글이 없어요</Text>
          <Text style={[styles.emptyBody, { color: palette.muted }]}>
            내가 남긴 글에 누군가 답하면{'\n'}여기에서 바로 읽을 수 있어요.
          </Text>
        </View>
      ) : (
        <FlatList
          data={replies}
          keyExtractor={(r) => String(r.id)}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={[styles.sep, { backgroundColor: palette.ruleSoft }]} />}
          renderItem={({ item }) => (
            <ReplyItem reply={item} unread={isAfter(item.createdAt, seenBefore)} />
          )}
          ListFooterComponent={
            <Text style={[styles.footer, { color: palette.muted }]}>
              앞뒤 대화는 그 자리에 가면 이어서 읽을 수 있어요.
            </Text>
          }
        />
      )}
    </ScreenModal>
  );
}

function ReplyItem({ reply, unread }: { reply: Reply; unread: boolean }) {
  const { palette } = useTheme();
  return (
    <View style={styles.item}>
      <Text numberOfLines={2} style={[styles.myQuote, { color: palette.muted, borderLeftColor: palette.rule }]}>
        {reply.myMessage.content}
      </Text>
      <View style={styles.replyRow}>
        <View style={styles.markerSlot}>
          <Marker marker={reply.marker} />
        </View>
        <Text style={[styles.replyText, { color: palette.ink }]}>{reply.content}</Text>
      </View>
      <View style={styles.meta}>
        {unread && (
          <View
            accessibilityLabel="새 답글"
            style={[styles.unreadDot, { backgroundColor: palette.markers[1] }]}
          />
        )}
        <Text style={[styles.time, { color: palette.muted }]}>{formatRelative(reply.createdAt)}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, paddingHorizontal: 44 },
  emptyTitle: { fontFamily: font.sansSemiBold, fontSize: 17, textAlign: 'center' },
  emptyBody: { fontFamily: font.sans, fontSize: 14, lineHeight: 23, textAlign: 'center' },
  list: { paddingVertical: 8 },
  sep: { height: StyleSheet.hairlineWidth, marginHorizontal: spacing.screenX },
  item: { paddingHorizontal: spacing.screenX, paddingVertical: 16, gap: 8 },
  myQuote: {
    fontFamily: font.sans,
    fontSize: 12.5,
    lineHeight: 18,
    borderLeftWidth: 2,
    paddingLeft: 9,
  },
  replyRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.markerGap },
  markerSlot: { width: 12, paddingTop: 6 },
  replyText: { flex: 1, fontFamily: font.sans, fontSize: 15, lineHeight: 24 },
  meta: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingLeft: 12 + spacing.markerGap },
  unreadDot: { width: 6, height: 6, borderRadius: 3 },
  time: { fontFamily: font.mono, fontSize: 10.5, letterSpacing: 0.5 },
  footer: {
    fontFamily: font.sans,
    fontSize: 12.5,
    lineHeight: 19,
    textAlign: 'center',
    paddingVertical: 24,
    paddingHorizontal: spacing.screenX,
  },
});
