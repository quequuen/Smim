import { StyleSheet, Text, View } from 'react-native';
import { TERMS_BODY } from '../content/terms';
import { font } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';

/** 이용약관 전문. 설정과 첫 실행 동의 화면이 함께 쓴다 */
export function TermsBody() {
  const { palette } = useTheme();
  return (
    <View style={styles.wrap}>
      {TERMS_BODY.map((s) => (
        <View key={s.title} style={styles.section}>
          <Text style={[styles.title, { color: palette.ink }]}>{s.title}</Text>
          <Text style={[styles.body, { color: palette.ink2 }]}>{s.body}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: 22 },
  section: { gap: 8 },
  title: { fontFamily: font.sansSemiBold, fontSize: 14.5 },
  body: { fontFamily: font.sans, fontSize: 14, lineHeight: 23 },
});
