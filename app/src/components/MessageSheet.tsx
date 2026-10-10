import { StyleSheet, Text, View } from 'react-native';
import type { Message } from '../api';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';
import { Sheet, SheetAction } from './Sheet';

/**
 * 메시지를 길게 누르면 뜨는 메뉴. 어떤 글에 대한 메뉴인지 맨 위에 보여준다.
 */
export function MessageSheet({
  message,
  onClose,
  onReply,
}: {
  message: Message | null;
  onClose: () => void;
  onReply: (m: Message) => void;
}) {
  const { palette } = useTheme();

  return (
    <Sheet visible={message !== null} onClose={onClose}>
      {message && (
        <>
          <View style={[styles.preview, { borderBottomColor: palette.ruleSoft }]}>
            <Text numberOfLines={2} style={[styles.previewText, { color: palette.muted }]}>
              {message.content}
            </Text>
          </View>
          <SheetAction label="답글 달기" onPress={() => onReply(message)} />
        </>
      )}
    </Sheet>
  );
}

const styles = StyleSheet.create({
  preview: {
    paddingHorizontal: spacing.screenX + 4,
    paddingTop: 6,
    paddingBottom: 14,
    marginBottom: 4,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  previewText: {
    fontFamily: font.sans,
    fontSize: 13.5,
    lineHeight: 20,
  },
});
