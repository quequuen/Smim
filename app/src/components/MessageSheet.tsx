import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { REPORT_REASONS, type Message, type ReportReason } from '../api';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';
import { Sheet, SheetAction } from './Sheet';

/**
 * 메시지를 길게 누르면 뜨는 메뉴. 어떤 글에 대한 메뉴인지 맨 위에 보여준다.
 *
 *   내 글   답글 달기 · 지우기
 *   남의 글 답글 달기 · 신고하기 · 이 작성자 글 그만 보기
 *
 * 신고는 같은 시트 안에서 사유 선택으로 넘어간다 — 시트를 두 번 띄우면 Modal 이 겹친다.
 */
export function MessageSheet({
  message,
  onClose,
  onReply,
  onDelete,
  onReport,
  onBlock,
}: {
  message: Message | null;
  onClose: () => void;
  onReply: (m: Message) => void;
  onDelete: (m: Message) => void;
  onReport: (m: Message, reason: ReportReason) => void;
  onBlock: (m: Message) => void;
}) {
  const { palette } = useTheme();
  const [step, setStep] = useState<'menu' | 'report'>('menu');

  useEffect(() => {
    if (message) setStep('menu');
  }, [message]);

  return (
    <Sheet visible={message !== null} onClose={onClose}>
      {message && (
        <>
          <View style={[styles.preview, { borderBottomColor: palette.ruleSoft }]}>
            {step === 'report' && <Text style={[styles.title, { color: palette.ink }]}>신고 사유</Text>}
            <Text numberOfLines={2} style={[styles.previewText, { color: palette.muted }]}>
              {message.content}
            </Text>
          </View>

          {step === 'menu' ? (
            <>
              <SheetAction label="답글 달기" onPress={() => onReply(message)} />
              {message.isMine ? (
                <SheetAction label="지우기" tone="danger" onPress={() => onDelete(message)} />
              ) : (
                <>
                  <SheetAction label="신고하기" tone="danger" onPress={() => setStep('report')} />
                  <SheetAction label="이 작성자 글 그만 보기" onPress={() => onBlock(message)} />
                </>
              )}
            </>
          ) : (
            <>
              {REPORT_REASONS.map((r) => (
                <SheetAction key={r.value} label={r.label} onPress={() => onReport(message, r.value)} />
              ))}
              <Text style={[styles.note, { color: palette.muted }]}>
                신고한 글은 바로 보이지 않게 돼요. 신고가 쌓이면 다른 사람에게도 가려집니다.
              </Text>
            </>
          )}
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
    gap: 6,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  title: {
    fontFamily: font.sansSemiBold,
    fontSize: 15,
  },
  previewText: {
    fontFamily: font.sans,
    fontSize: 13.5,
    lineHeight: 20,
  },
  note: {
    paddingHorizontal: spacing.screenX + 4,
    paddingTop: 10,
    paddingBottom: 4,
    fontFamily: font.sans,
    fontSize: 12.5,
    lineHeight: 19,
  },
});
