import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { api, type Block } from '../api';
import { getMockScenario, MOCK_SCENARIOS, setMockScenario, type MockScenario } from '../api/mockClient';
import { ChevronIcon } from '../components/Icons';
import { ScreenModal } from '../components/ScreenModal';
import { TermsBody } from '../components/TermsBody';
import { formatDate } from '../lib/time';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';

type Page = 'main' | 'blocks' | 'terms';

const TITLES: Record<Page, string> = { main: '설정', blocks: '차단한 작성자', terms: '이용약관' };

/**
 * 설정. 계정이 없으므로 항목이 적다 — 차단 목록, 약관, 익명 키 안내.
 * 한 단계 들어가는 화면은 Modal 을 겹치지 않고 같은 Modal 안에서 바꾼다.
 */
export function SettingsScreen({
  visible,
  onClose,
  onBlocksChanged,
}: {
  visible: boolean;
  onClose: () => void;
  /** 차단 해제·mock 시나리오 변경 뒤 메인 목록을 다시 받아야 한다 */
  onBlocksChanged: () => void;
}) {
  const [page, setPage] = useState<Page>('main');
  const [blocks, setBlocks] = useState<Block[] | null>(null);
  const [blocksFailed, setBlocksFailed] = useState(false);

  const loadBlocks = () => {
    setBlocks(null);
    setBlocksFailed(false);
    api.getBlocks().then(setBlocks, () => setBlocksFailed(true));
  };

  useEffect(() => {
    if (!visible) return;
    setPage('main');
    loadBlocks();
  }, [visible]);

  const unblock = (b: Block) =>
    Alert.alert('차단을 해제할까요?', '이 작성자의 글이 다시 보여요.', [
      { text: '취소', style: 'cancel' },
      {
        text: '해제',
        onPress: async () => {
          try {
            await api.unblock(b.id);
            setBlocks((prev) => prev?.filter((x) => x.id !== b.id) ?? null);
            onBlocksChanged();
          } catch {
            Alert.alert('해제하지 못했어요', '잠시 후 다시 시도해 주세요.');
          }
        },
      },
    ]);

  return (
    <ScreenModal
      visible={visible}
      title={TITLES[page]}
      onClose={onClose}
      onBack={page === 'main' ? undefined : () => setPage('main')}
    >
      {page === 'main' && (
        <MainPage
          blockCount={blocks?.length ?? null}
          onOpen={setPage}
          onScenarioChanged={() => {
            loadBlocks();
            onBlocksChanged();
          }}
        />
      )}
      {page === 'blocks' && (
        <BlocksPage blocks={blocks} failed={blocksFailed} onRetry={loadBlocks} onUnblock={unblock} />
      )}
      {page === 'terms' && (
        <ScrollView contentContainerStyle={styles.termsScroll}>
          <TermsBody />
        </ScrollView>
      )}
    </ScreenModal>
  );
}

function MainPage({
  blockCount,
  onOpen,
  onScenarioChanged,
}: {
  blockCount: number | null;
  onOpen: (p: Page) => void;
  onScenarioChanged: () => void;
}) {
  const { palette } = useTheme();
  return (
    <ScrollView>
      <Row label="차단한 작성자" value={blockCount === null ? '' : `${blockCount}명`} onPress={() => onOpen('blocks')} />
      <Row label="이용약관" onPress={() => onOpen('terms')} />
      {/* TODO(#16): smim.app 에 개인정보처리방침을 올린 뒤 Linking.openURL 로 연다 */}
      <Row label="개인정보처리방침" value="준비 중" />

      <View style={[styles.note, { borderTopColor: palette.ruleSoft }]}>
        <Text style={[styles.noteTitle, { color: palette.ink2 }]}>계정 없이 쓰는 앱이에요</Text>
        <Text style={[styles.noteBody, { color: palette.muted }]}>
          내가 쓴 글은 이 기기에만 있는 무작위 키로 구분해요. 앱을 지우면 키도 함께 사라져서, 전에 쓴 글을 더 이상 지울
          수 없어요.
        </Text>
      </View>

      {__DEV__ && <MockScenarioPicker onChanged={onScenarioChanged} />}
    </ScrollView>
  );
}

/** 개발 빌드에서만. 오류 상태를 서버 없이 눌러보기 위한 것 */
function MockScenarioPicker({ onChanged }: { onChanged: () => void }) {
  const { palette } = useTheme();
  const [current, setCurrent] = useState<MockScenario>(getMockScenario());

  const pick = (s: MockScenario) => {
    setMockScenario(s);
    setCurrent(s);
    onChanged();
  };

  return (
    <View style={[styles.dev, { borderTopColor: palette.ruleSoft }]}>
      <Text style={[styles.devTitle, { color: palette.muted }]}>개발용 · mock 시나리오</Text>
      <View style={styles.devOptions}>
        {MOCK_SCENARIOS.map((s) => {
          const on = s.value === current;
          return (
            <Pressable
              key={s.value}
              accessibilityRole="radio"
              accessibilityState={{ selected: on }}
              onPress={() => pick(s.value)}
              style={[
                styles.devChip,
                { borderColor: on ? palette.ink : palette.rule, backgroundColor: on ? palette.ink : 'transparent' },
              ]}
            >
              <Text style={[styles.devChipLabel, { color: on ? palette.paper : palette.ink2 }]}>{s.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

function Row({ label, value, onPress }: { label: string; value?: string; onPress?: () => void }) {
  const { palette } = useTheme();
  return (
    <Pressable
      accessibilityRole={onPress ? 'button' : undefined}
      disabled={!onPress}
      onPress={onPress}
      style={({ pressed }) => [
        styles.row,
        { borderBottomColor: palette.ruleSoft },
        pressed && { backgroundColor: palette.ruleSoft },
      ]}
    >
      <Text style={[styles.rowLabel, { color: onPress ? palette.ink : palette.muted }]}>{label}</Text>
      <View style={styles.rowRight}>
        {!!value && <Text style={[styles.rowValue, { color: palette.muted }]}>{value}</Text>}
        {onPress && <ChevronIcon color={palette.muted} />}
      </View>
    </Pressable>
  );
}

function BlocksPage({
  blocks,
  failed,
  onRetry,
  onUnblock,
}: {
  blocks: Block[] | null;
  failed: boolean;
  onRetry: () => void;
  onUnblock: (b: Block) => void;
}) {
  const { palette } = useTheme();

  if (failed) {
    return (
      <View style={styles.center}>
        <Text style={[styles.emptyBody, { color: palette.muted }]}>차단 목록을 불러오지 못했어요</Text>
        <Pressable accessibilityRole="button" onPress={onRetry} hitSlop={8}>
          <Text style={[styles.retry, { color: palette.ink }]}>다시 시도</Text>
        </Pressable>
      </View>
    );
  }
  if (blocks === null) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={palette.muted} />
      </View>
    );
  }
  if (blocks.length === 0) {
    return (
      <View style={styles.center}>
        <Text style={[styles.emptyBody, { color: palette.muted }]}>차단한 작성자가 없어요</Text>
      </View>
    );
  }

  return (
    <FlatList
      data={blocks}
      keyExtractor={(b) => String(b.id)}
      ListHeaderComponent={
        // 누구인지 보여줄 수 없어서(D4) 차단할 때 고른 글로 알아보게 한다
        <Text style={[styles.blocksHint, { color: palette.muted }]}>차단할 때 고른 글로 표시돼요.</Text>
      }
      renderItem={({ item }) => (
        <View style={[styles.blockRow, { borderBottomColor: palette.ruleSoft }]}>
          <View style={styles.blockText}>
            <Text numberOfLines={2} style={[styles.blockContent, { color: palette.ink2 }]}>
              {item.messageContent ?? '지워진 글이에요'}
            </Text>
            <Text style={[styles.blockDate, { color: palette.muted }]}>{formatDate(item.createdAt)} 차단</Text>
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="차단 해제"
            onPress={() => onUnblock(item)}
            style={[styles.unblock, { borderColor: palette.rule }]}
          >
            <Text style={[styles.unblockLabel, { color: palette.ink }]}>해제</Text>
          </Pressable>
        </View>
      )}
    />
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  retry: { fontFamily: font.sansMedium, fontSize: 14, textDecorationLine: 'underline' },
  dev: { paddingHorizontal: spacing.screenX, paddingVertical: 20, gap: 12, borderTopWidth: StyleSheet.hairlineWidth },
  devTitle: { fontFamily: font.mono, fontSize: 11, letterSpacing: 0.8 },
  devOptions: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  devChip: { paddingHorizontal: 12, height: 32, borderRadius: 16, borderWidth: StyleSheet.hairlineWidth, justifyContent: 'center' },
  devChipLabel: { fontFamily: font.sans, fontSize: 12.5 },
  emptyBody: { fontFamily: font.sans, fontSize: 14 },
  row: {
    minHeight: 56,
    paddingHorizontal: spacing.screenX,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  rowLabel: { fontFamily: font.sans, fontSize: 15.5 },
  rowRight: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  rowValue: { fontFamily: font.sans, fontSize: 14 },
  note: { paddingHorizontal: spacing.screenX, paddingVertical: 24, gap: 6 },
  noteTitle: { fontFamily: font.sansMedium, fontSize: 13.5 },
  noteBody: { fontFamily: font.sans, fontSize: 13, lineHeight: 21 },
  termsScroll: { padding: spacing.screenX, paddingBottom: 40 },
  blocksHint: {
    fontFamily: font.sans,
    fontSize: 12.5,
    paddingHorizontal: spacing.screenX,
    paddingTop: 16,
    paddingBottom: 6,
  },
  blockRow: {
    paddingHorizontal: spacing.screenX,
    paddingVertical: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  blockText: { flex: 1, gap: 4 },
  blockContent: { fontFamily: font.sans, fontSize: 14, lineHeight: 21 },
  blockDate: { fontFamily: font.mono, fontSize: 10.5 },
  unblock: {
    height: 34,
    paddingHorizontal: 14,
    borderRadius: 17,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    justifyContent: 'center',
  },
  unblockLabel: { fontFamily: font.sansMedium, fontSize: 13.5 },
});
