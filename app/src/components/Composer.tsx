import { forwardRef } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { CONTENT_MAX, type Message } from '../api';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';
import { CloseIcon, SendIcon } from './Icons';

/** 이만큼 쓰면 글자 수를 보여준다. 처음부터 보이면 짧게 쓰라는 압박이 된다 */
const COUNTER_FROM = CONTENT_MAX - 50;

type Props = {
  value: string;
  onChangeText: (text: string) => void;
  onSend: () => void;
  sending: boolean;
  /** 위치를 아직 모르면 보낼 수 없다 — 글은 좌표에 남기 때문이다 */
  canSend: boolean;
  replyTo: Message | null;
  onCancelReply: () => void;
  emptyPlace: boolean;
  bottomPadding: number;
};

export const Composer = forwardRef<TextInput, Props>(function Composer(
  { value, onChangeText, onSend, sending, canSend, replyTo, onCancelReply, emptyPlace, bottomPadding },
  inputRef,
) {
  const { palette } = useTheme();
  const length = value.trim().length;
  const over = value.length > CONTENT_MAX;
  const sendable = canSend && !sending && length > 0 && !over;

  return (
    <View style={[styles.wrap, { borderTopColor: palette.ruleSoft, paddingBottom: bottomPadding }]}>
      {replyTo && (
        <View style={styles.replyBar}>
          <Text numberOfLines={1} style={[styles.replyQuote, { color: palette.muted, borderLeftColor: palette.rule }]}>
            {replyTo.content}
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="답글 취소"
            onPress={onCancelReply}
            hitSlop={12}
            style={styles.replyCancel}
          >
            <CloseIcon color={palette.muted} />
          </Pressable>
        </View>
      )}

      <View style={styles.row}>
        <TextInput
          ref={inputRef}
          value={value}
          onChangeText={onChangeText}
          multiline
          accessibilityLabel="이 자리에 남길 말"
          placeholder={replyTo ? '답글 남기기' : emptyPlace ? '이 자리에 처음으로 남기기' : '이 자리에 남기기'}
          placeholderTextColor={palette.muted}
          style={[
            styles.input,
            {
              color: palette.ink,
              backgroundColor: palette.surface,
              borderColor: emptyPlace && !replyTo ? palette.muted : palette.rule,
            },
          ]}
        />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="남기기"
          accessibilityState={{ disabled: !sendable }}
          disabled={!sendable}
          onPress={onSend}
          style={[styles.send, { backgroundColor: sendable ? palette.ink : palette.rule }]}
        >
          {sending ? <ActivityIndicator size="small" color={palette.paper} /> : <SendIcon color={palette.paper} />}
        </Pressable>
      </View>

      {value.length >= COUNTER_FROM && (
        <Text style={[styles.counter, { color: over ? palette.markers[1] : palette.muted }]}>
          {value.length}/{CONTENT_MAX}
        </Text>
      )}
    </View>
  );
});

const styles = StyleSheet.create({
  wrap: {
    paddingHorizontal: 16,
    paddingTop: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
    gap: 8,
  },
  replyBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingLeft: 4,
  },
  replyQuote: {
    flex: 1,
    fontFamily: font.sans,
    fontSize: 12.5,
    lineHeight: 18,
    borderLeftWidth: 2,
    paddingLeft: 9,
  },
  replyCancel: {
    width: 24,
    height: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 10,
  },
  input: {
    flex: 1,
    minHeight: spacing.touchTarget,
    maxHeight: 120,
    paddingHorizontal: 16,
    // multiline 에서 한 줄일 때 가운데에 오도록 위아래를 맞춘다
    paddingTop: 12,
    paddingBottom: 12,
    fontFamily: font.sans,
    fontSize: 15,
    lineHeight: 20,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 22,
  },
  send: {
    width: spacing.touchTarget,
    height: spacing.touchTarget,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  counter: {
    alignSelf: 'flex-end',
    marginRight: spacing.touchTarget + 10,
    fontFamily: font.mono,
    fontSize: 10.5,
  },
});
