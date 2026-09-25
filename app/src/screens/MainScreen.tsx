import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  SectionList,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { api, DEFAULT_CONFIG, isServerConnected, type Message, type RuntimeConfig } from '../api';
import { MOCK_EMPTY } from '../api/mockClient';
import { MessageRow } from '../components/MessageRow';
import { MenuIcon, PlaceMarkIcon, ReplyIcon, SendIcon } from '../components/Icons';
import { TimeSeparator } from '../components/TimeSeparator';
import { dayKey, isWideGap } from '../lib/time';
import { useLocation } from '../lib/useLocation';
import { usePresence, type PresenceState } from '../lib/usePresence';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';

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

  // 실패하면 내장 기본값으로 폴백한다 (docs/location-policy.md 3-2)
  const [config, setConfig] = useState<RuntimeConfig>(DEFAULT_CONFIG);
  useEffect(() => {
    api.getConfig().then(setConfig).catch(() => {});
  }, []);

  // distanceInterval 이 move_threshold 라서 콜백이 곧 "화면 재조회할 만큼 움직였다" 는 뜻이다
  const location = useLocation(config.moveThresholdM);
  const coords = location.status === 'ready' ? location.coords : null;
  const presence = usePresence(coords, config.heartbeatSec);

  useEffect(() => {
    if (!coords) return;
    let alive = true;
    api
      .getMessages({ at: coords, radiusM: config.radiusM })
      .then((page) => {
        if (alive) setMessages(page.messages);
      })
      .catch(() => {
        if (alive) setMessages([]);
      });
    return () => {
      alive = false;
    };
  }, [coords, config.radiusM]);

  const sections = useMemo(() => (messages ? buildSections(messages) : []), [messages]);
  const isEmpty = messages !== null && messages.length === 0;

  return (
    <View style={[styles.root, { backgroundColor: palette.paper }]}>
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
            accessibilityLabel="내 글에 달린 답글"
            style={styles.iconButton}
          >
            <ReplyIcon color={palette.ink2} />
            <View style={[styles.badge, { backgroundColor: palette.markers[1] }]} />
          </Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel="설정" style={styles.iconButton}>
            <MenuIcon color={palette.ink2} />
          </Pressable>
        </View>
      </View>

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
          renderItem={({ item }) => <MessageRow message={item} />}
          renderSectionHeader={({ section }) => (
            <TimeSeparator iso={section.iso} wideGap={section.wideGap} />
          )}
          ItemSeparatorComponent={() => <View style={{ height: spacing.messageGap }} />}
          contentContainerStyle={styles.listContent}
          stickySectionHeadersEnabled={false}
          onContentSizeChange={() => listRef.current?.getScrollResponder()?.scrollToEnd({ animated: false })}
        />
      )}

      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={[styles.composer, { borderTopColor: palette.ruleSoft }]}>
          <TextInput
            accessibilityLabel="이 자리에 남길 말"
            placeholder={isEmpty ? '이 자리에 처음으로 남기기' : '이 자리에 남기기'}
            placeholderTextColor={palette.muted}
            style={[
              styles.input,
              {
                color: palette.ink,
                backgroundColor: palette.surface,
                borderColor: isEmpty ? palette.muted : palette.rule,
              },
            ]}
          />
          <Pressable accessibilityRole="button" accessibilityLabel="남기기" style={[styles.send, { backgroundColor: palette.ink }]}>
            <SendIcon color={palette.paper} />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </View>
  );
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
  root: {
    flex: 1,
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight ?? 0 : 44,
  },
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
  composer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 20,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  input: {
    flex: 1,
    height: spacing.touchTarget,
    paddingHorizontal: 16,
    fontFamily: font.sans,
    fontSize: 15,
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
});

export { MOCK_EMPTY };
