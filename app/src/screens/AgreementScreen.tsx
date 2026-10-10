import { useState } from 'react';
import { Pressable, ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { TermsBody } from '../components/TermsBody';
import { TERMS_SUMMARY } from '../content/terms';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';

/**
 * 첫 실행 이용약관 동의. 스토어 심사 필수 — 불쾌 콘텐츠 금지 약관에 동의해야 글을 볼 수 있다.
 *
 * 위치 권한보다 먼저 띄운다. 무엇을 하는 앱인지 알기 전에 권한부터 묻지 않는다.
 */
export function AgreementScreen({ onAgree }: { onAgree: () => void }) {
  const { palette, isDark } = useTheme();
  const insets = useSafeAreaInsets();
  const [showFull, setShowFull] = useState(false);

  return (
    <View style={[styles.root, { backgroundColor: palette.paper, paddingTop: insets.top }]}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={[styles.brand, { color: palette.muted }]}>스밈</Text>
        <Text style={[styles.title, { color: palette.ink }]}>
          이 자리에 남긴 글은{'\n'}나중에 오는 사람에게 읽혀요
        </Text>
        <Text style={[styles.lead, { color: palette.ink2 }]}>
          이름 없이 쓰는 대신, 함께 지켜야 할 약속이 있어요.
        </Text>

        <View style={styles.rules}>
          {TERMS_SUMMARY.map((rule, i) => (
            <View key={rule} style={styles.rule}>
              <Text style={[styles.ruleNo, { color: palette.muted }]}>{String(i + 1).padStart(2, '0')}</Text>
              <Text style={[styles.ruleText, { color: palette.ink }]}>{rule}</Text>
            </View>
          ))}
        </View>

        <Pressable accessibilityRole="button" onPress={() => setShowFull((v) => !v)} hitSlop={8}>
          <Text style={[styles.toggle, { color: palette.ink2 }]}>
            {showFull ? '이용약관 접기' : '이용약관 전문 보기'}
          </Text>
        </Pressable>
        {showFull && (
          <View style={[styles.full, { borderTopColor: palette.ruleSoft }]}>
            <TermsBody />
          </View>
        )}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 20), borderTopColor: palette.ruleSoft }]}>
        <Pressable
          accessibilityRole="button"
          onPress={onAgree}
          style={({ pressed }) => [styles.agree, { backgroundColor: palette.ink, opacity: pressed ? 0.85 : 1 }]}
        >
          <Text style={[styles.agreeLabel, { color: palette.paper }]}>동의하고 시작하기</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  scroll: { paddingHorizontal: spacing.screenX + 8, paddingTop: 48, paddingBottom: 32 },
  brand: { fontFamily: font.mono, fontSize: 12, letterSpacing: 1.68 },
  title: { fontFamily: font.sansSemiBold, fontSize: 22, lineHeight: 33, marginTop: 14 },
  lead: { fontFamily: font.sans, fontSize: 14.5, lineHeight: 23, marginTop: 12 },
  rules: { marginTop: 32, gap: 18 },
  rule: { flexDirection: 'row', gap: 14 },
  ruleNo: { fontFamily: font.mono, fontSize: 11, paddingTop: 4 },
  ruleText: { flex: 1, fontFamily: font.sans, fontSize: 15, lineHeight: 24 },
  toggle: { fontFamily: font.sansMedium, fontSize: 13.5, marginTop: 32, textDecorationLine: 'underline' },
  full: { marginTop: 20, paddingTop: 20, borderTopWidth: StyleSheet.hairlineWidth },
  footer: { paddingHorizontal: 16, paddingTop: 12, borderTopWidth: StyleSheet.hairlineWidth },
  agree: { height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center' },
  agreeLabel: { fontFamily: font.sansSemiBold, fontSize: 15.5 },
});
