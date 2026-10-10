import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  AppState,
  Keyboard,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  SectionList,
  StatusBar,
  StyleSheet,
  Text,
  type TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  api,
  ApiError,
  DEFAULT_CONFIG,
  isServerConnected,
  type Message,
  type Reply,
  type ReportReason,
  type RuntimeConfig,
} from '../api';
import { MessageRow } from '../components/MessageRow';
import { Composer } from '../components/Composer';
import { MenuIcon, PlaceMarkIcon, ReplyIcon } from '../components/Icons';
import { MessageSheet } from '../components/MessageSheet';
import { TimeSeparator } from '../components/TimeSeparator';
import { Toast, useToast } from '../components/Toast';
import { getItem, KEYS, setItem } from '../lib/storage';
import { dayKey, isAfter, isWideGap } from '../lib/time';
import { useLocation } from '../lib/useLocation';
import { usePresence, type PresenceState } from '../lib/usePresence';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';
import { RepliesScreen } from './RepliesScreen';
import { SettingsScreen } from './SettingsScreen';

type Section = { key: string; iso: string; wideGap: boolean; data: Message[] };

/**
 * 날짜별로 묶어 SectionList 섹션을 만든다.
 * 오래된 것이 위, 최신이 아래 — 사용자는 위로 올리며 과거를 탐색한다.
 */
function buildSections(messages: Message[]): Section[] {
  const oldestFirst = [...messages].sort((a, b) => a.createdAt.localeCompare(b.createdAt));

  const sections: Section[] = [];
  for (const m of oldestFirst) {
    const key = dayKey(m.createdAt);
    const last = sections[sections.length - 1];
    if (last && last.key === key) {
      last.data.push(m);
    } else {
      sections.push({ key, iso: m.createdAt, wideGap: false, data: [m] });
    }
  }

  // 직전 블록과 얼마나 벌어졌는지는 섹션이 다 만들어진 뒤에야 안다
  for (let i = 1; i < sections.length; i += 1) {
    sections[i].wideGap = isWideGap(sections[i - 1].iso, sections[i].iso);
  }
  return sections;
}

export function MainScreen() {
  const { palette, isDark } = useTheme();
  const [messages, setMessages] = useState<Message[] | null>(null);
  const listRef = useRef<SectionList<Message, Section>>(null);
  const insets = useSafeAreaInsets();
  const keyboardShown = useKeyboardShown();

  // 키보드가 올라오면 목록이 줄어든다 — 최신 메시지가 가려지지 않도록 바닥으로 붙인다
  useEffect(() => {
    if (keyboardShown) listRef.current?.getScrollResponder()?.scrollToEnd({ animated: true });
  }, [keyboardShown]);

  // 실패하면 내장 기본값으로 폴백한다 (docs/location-policy.md 3-2)
  const [config, setConfig] = useState<RuntimeConfig>(DEFAULT_CONFIG);
  useEffect(() => {
    api.getConfig().then(setConfig).catch(() => {});
  }, []);

  // distanceInterval 이 move_threshold 라서 콜백이 곧 "화면 재조회할 만큼 움직였다" 는 뜻이다
  const location = useLocation(config.moveThresholdM);
  const coords = location.status === 'ready' ? location.coords : null;
  const presence = usePresence(coords, config.heartbeatSec);

  const load = useCallback(async () => {
    if (!coords) return;
    try {
      const page = await api.getMessages({ at: coords, radiusM: config.radiusM });
      setMessages(page.messages);
    } catch {
      setMessages((prev) => prev ?? []);
    }
  }, [coords, config.radiusM]);

  useEffect(() => {
    load();
  }, [load]);

  // ── 내 글에 달린 답글 (D6) ──────────────
  // 읽음은 서버가 아니라 기기에 남긴 "마지막으로 연 시각"으로 판단한다
  const [replies, setReplies] = useState<Reply[] | null>(null);
  const [seenAt, setSeenAt] = useState<string | null>(null);
  const [seenBefore, setSeenBefore] = useState<string | null>(null);
  const [repliesOpen, setRepliesOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);

  const loadReplies = useCallback(() => {
    api.getReplies().then(setReplies, () => {});
  }, []);

  useEffect(() => {
    getItem(KEYS.repliesSeenAt).then(setSeenAt);
    loadReplies();
    // 앱을 다시 열 때 새 답글이 왔는지 본다 — "앱을 열면 내 글에 답글 N개" (D6)
    const sub = AppState.addEventListener('change', (s) => s === 'active' && loadReplies());
    return () => sub.remove();
  }, [loadReplies]);

  const unreadCount = replies?.filter((r) => isAfter(r.createdAt, seenAt)).length ?? 0;

  const openReplies = () => {
    setSeenBefore(seenAt); // 이번에 보여줄 새 답글 표시는 열기 전 시각 기준
    const now = new Date().toISOString();
    setSeenAt(now);
    setItem(KEYS.repliesSeenAt, now);
    setRepliesOpen(true);
    loadReplies();
  };

  // ── 글쓰기 ─────────────────────────────
  const inputRef = useRef<TextInput>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [replyTo, setReplyTo] = useState<Message | null>(null);
  const [sheetFor, setSheetFor] = useState<Message | null>(null);

  const send = async () => {
    const content = draft.trim();
    if (!coords || !content || sending) return;
    setSending(true);
    try {
      const created = await api.postMessage({ at: coords, content, replyToId: replyTo?.id });
      setMessages((prev) => [...(prev ?? []), created]);
      setDraft('');
      setReplyTo(null);
    } catch {
      // 실패하면 쓴 글을 지우지 않고 남겨 둔다. 오류 안내는 오류 상태 작업에서 붙인다
    } finally {
      setSending(false);
    }
  };

  const startReply = (m: Message) => {
    setSheetFor(null);
    setReplyTo(m);
    // 시트가 닫히는 애니메이션과 겹치면 Android 에서 키보드가 안 뜬다
    setTimeout(() => inputRef.current?.focus(), 250);
  };

  // ── 지우기 · 신고 · 차단 ─────────────────
  const { toast, show: showToast, clear: clearToast } = useToast();

  /** 목록에서 빠진 글을 인용 중이었다면 인용도 푼다 */
  const dropReplyIfGone = (ids: number[]) => setReplyTo((r) => (r && ids.includes(r.id) ? null : r));

  const confirmDelete = (m: Message) => {
    setSheetFor(null);
    Alert.alert('이 글을 지울까요?', '지운 글은 되돌릴 수 없어요.\n이 글을 인용한 답글에는 "지워진 글"로 보여요.', [
      { text: '취소', style: 'cancel' },
      {
        text: '지우기',
        style: 'destructive',
        onPress: async () => {
          try {
            await api.deleteMessage(m.id);
            dropReplyIfGone([m.id]);
            await load(); // 이 글을 인용한 답글도 함께 바뀌므로 다시 받는다
            loadReplies();
            showToast('지웠어요');
          } catch (e) {
            showToast(e instanceof ApiError ? e.message : '지우지 못했어요. 잠시 후 다시 시도해 주세요.');
          }
        },
      },
    ]);
  };

  const report = async (m: Message, reason: ReportReason) => {
    setSheetFor(null);
    try {
      await api.reportMessage(m.id, reason);
      dropReplyIfGone([m.id]);
      await load();
      loadReplies();
      showToast('신고했어요. 이 글은 이제 보이지 않아요.');
    } catch (e) {
      showToast(e instanceof ApiError ? e.message : '신고하지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
  };

  const confirmBlock = (m: Message) => {
    setSheetFor(null);
    Alert.alert(
      '이 작성자의 글을 그만 볼까요?',
      '이 사람이 쓴 글이 지금도, 앞으로도 보이지 않아요.\n설정의 차단 목록에서 되돌릴 수 있어요.',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '그만 보기',
          style: 'destructive',
          onPress: async () => {
            try {
              const before = new Set((messages ?? []).map((x) => x.id));
              await api.blockAuthor(m.id);
              const page = await api.getMessages({ at: coords!, radiusM: config.radiusM });
              // 누구 글이 빠졌는지는 앱이 모른다 — 다시 받은 목록과 비교해 인용을 정리한다
              const after = new Set(page.messages.map((x) => x.id));
              dropReplyIfGone([...before].filter((id) => !after.has(id)));
              setMessages(page.messages);
              loadReplies();
              showToast('이 작성자의 글을 더 이상 보지 않아요');
            } catch (e) {
              showToast(e instanceof ApiError ? e.message : '차단하지 못했어요. 잠시 후 다시 시도해 주세요.');
            }
          },
        },
      ],
    );
  };

  const sections = useMemo(() => (messages ? buildSections(messages) : []), [messages]);
  const isEmpty = messages !== null && messages.length === 0;

  return (
    // 화면 전체를 감싸야 목록이 줄어들고 입력창이 키보드 위로 올라온다.
    // Android 도 edge-to-edge 라 창이 리사이즈되지 않으므로 iOS 와 같이 padding 을 쓴다
    <KeyboardAvoidingView
      behavior="padding"
      style={[styles.root, { backgroundColor: palette.paper, paddingTop: insets.top }]}
    >
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />

      <View style={[styles.header, { borderBottomColor: palette.ruleSoft }]}>
        <View>
          <Text style={[styles.headerLabel, { color: palette.muted }]}>이 근처</Text>
          {__DEV__ && isServerConnected && (
            <Text style={[styles.devStatus, { color: palette.muted }]}>{describePresence(presence)}</Text>
          )}
        </View>
        <View style={styles.headerActions}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={unreadCount > 0 ? `내 글에 달린 답글, 새 답글 ${unreadCount}개` : '내 글에 달린 답글'}
            onPress={openReplies}
            style={styles.iconButton}
          >
            <ReplyIcon color={palette.ink2} />
            {unreadCount > 0 && <View style={[styles.badge, { backgroundColor: palette.markers[1] }]} />}
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="설정"
            onPress={() => setSettingsOpen(true)}
            style={styles.iconButton}
          >
            <MenuIcon color={palette.ink2} />
          </Pressable>
        </View>
      </View>

      {/* 토스트를 입력창 바로 위에 띄우기 위해 본문을 한 겹 감싼다 */}
      <View style={styles.body}>
        {location.status === 'denied' || location.status === 'error' ? (
          <View style={styles.center}>
            <PlaceMarkIcon color={palette.rule} dot={palette.muted} />
            <View style={styles.emptyText}>
              <Text style={[styles.emptyTitle, { color: palette.ink }]}>
                {location.status === 'denied' ? '위치를 알아야\n이 자리의 기록을 보여드려요' : '지금 위치를\n확인하지 못했어요'}
              </Text>
              <Text style={[styles.emptyBody, { color: palette.muted }]}>
                앱이 켜져 있을 때만 확인하며,{'\n'}위치를 다른 사용자에게 공개하지 않습니다.
              </Text>
            </View>
            {location.status === 'denied' && (
              <Pressable
                accessibilityRole="button"
                onPress={() => Linking.openSettings()}
                style={[styles.settingsButton, { borderColor: palette.rule }]}
              >
                <Text style={[styles.settingsLabel, { color: palette.ink }]}>설정에서 허용하기</Text>
              </Pressable>
            )}
          </View>
        ) : messages === null ? (
          <View style={styles.center}>
            <ActivityIndicator color={palette.muted} />
          </View>
        ) : isEmpty ? (
          <View style={styles.center}>
            <PlaceMarkIcon color={palette.rule} dot={palette.muted} />
            <View style={styles.emptyText}>
              <Text style={[styles.emptyTitle, { color: palette.ink }]}>
                이 자리엔 아직{'\n'}아무 말도 없어요
              </Text>
              <Text style={[styles.emptyBody, { color: palette.muted }]}>
                처음으로 남겨보세요.{'\n'}이 글은 사라지지 않고 이 자리에 남아,{'\n'}나중에 오는
                사람에게 읽힙니다.
              </Text>
            </View>
          </View>
        ) : (
          <SectionList
            ref={listRef}
            sections={sections}
            keyExtractor={(item) => String(item.id)}
            renderItem={({ item }) => <MessageRow message={item} onLongPress={setSheetFor} />}
            renderSectionHeader={({ section }) => (
              <TimeSeparator iso={section.iso} wideGap={section.wideGap} />
            )}
            ItemSeparatorComponent={() => <View style={{ height: spacing.messageGap }} />}
            contentContainerStyle={styles.listContent}
            stickySectionHeadersEnabled={false}
            keyboardDismissMode={Platform.OS === 'ios' ? 'interactive' : 'on-drag'}
            keyboardShouldPersistTaps="handled"
            onContentSizeChange={() => listRef.current?.getScrollResponder()?.scrollToEnd({ animated: false })}
          />
        )}
        <Toast toast={toast} onDone={clearToast} />
      </View>

      <Composer
        ref={inputRef}
        value={draft}
        onChangeText={setDraft}
        onSend={send}
        sending={sending}
        canSend={coords !== null}
        replyTo={replyTo}
        onCancelReply={() => setReplyTo(null)}
        emptyPlace={isEmpty}
        // 키보드가 떠 있으면 홈 인디케이터가 키보드 뒤로 숨으므로 하단 inset 을 더하지 않는다
        bottomPadding={keyboardShown ? 12 : Math.max(insets.bottom, 20)}
      />

      <RepliesScreen
        visible={repliesOpen}
        onClose={() => setRepliesOpen(false)}
        replies={replies}
        seenBefore={seenBefore}
      />
      <SettingsScreen
        visible={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        onBlocksChanged={() => {
          load();
          loadReplies();
        }}
      />

      <MessageSheet
        message={sheetFor}
        onClose={() => setSheetFor(null)}
        onReply={startReply}
        onDelete={confirmDelete}
        onReport={report}
        onBlock={confirmBlock}
      />
    </KeyboardAvoidingView>
  );
}

function useKeyboardShown(): boolean {
  const [shown, setShown] = useState(false);
  useEffect(() => {
    // iOS 는 will 이벤트가 있어 애니메이션과 맞출 수 있다. Android 는 did 만 온다
    const showEvt = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
    const hideEvt = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';
    const subs = [
      Keyboard.addListener(showEvt, () => setShown(true)),
      Keyboard.addListener(hideEvt, () => setShown(false)),
    ];
    return () => subs.forEach((sub) => sub.remove());
  }, []);
  return shown;
}

/** 개발 중에만 보이는 서버 통신 상태 */
function describePresence(p: PresenceState): string {
  switch (p.status) {
    case 'idle':
      return 'server · 위치 대기';
    case 'ok':
      return `server · ok ${p.at.toTimeString().slice(0, 8)}`;
    case 'failed':
      return `server · 실패 ${p.error instanceof Error ? p.error.message : ''}`;
  }
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  body: { flex: 1 },
  header: {
    height: spacing.headerHeight,
    paddingLeft: spacing.screenX,
    paddingRight: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerLabel: {
    fontFamily: font.mono,
    fontSize: 12,
    letterSpacing: 1.68, // 0.14em × 12px
  },
  devStatus: {
    fontFamily: font.mono,
    fontSize: 10,
    marginTop: 2,
  },
  headerActions: { flexDirection: 'row', alignItems: 'center' },
  iconButton: {
    width: spacing.touchTarget,
    height: spacing.touchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badge: {
    position: 'absolute',
    top: 10,
    right: 11,
    width: 7,
    height: 7,
    borderRadius: 3.5,
  },
  listContent: { paddingBottom: 16 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 22, paddingHorizontal: 44 },
  emptyText: { alignItems: 'center', gap: 10 },
  emptyTitle: {
    fontFamily: font.sansSemiBold,
    fontSize: 18,
    lineHeight: 27,
    textAlign: 'center',
  },
  emptyBody: {
    fontFamily: font.sans,
    fontSize: 14.5,
    lineHeight: 25,
    textAlign: 'center',
  },
  settingsButton: {
    height: spacing.touchTarget,
    paddingHorizontal: 20,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  settingsLabel: {
    fontFamily: font.sansMedium,
    fontSize: 14.5,
  },
});

