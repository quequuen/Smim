import { useCallback, useEffect, useRef, useState } from 'react';
import { Animated, StyleSheet, Text } from 'react-native';
import { font } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';

const SHOW_MS = 2600;

/** 동작이 반영됐다는 짧은 안내. 화면 아래, 입력창 위에 잠깐 떴다 사라진다 */
export function useToast() {
  const [text, setText] = useState<string | null>(null);
  const [seq, setSeq] = useState(0);
  const show = useCallback((t: string) => {
    setText(t);
    setSeq((n) => n + 1);
  }, []);
  return { toast: text ? { text, seq } : null, show, clear: () => setText(null) };
}

export function Toast({ toast, onDone }: { toast: { text: string; seq: number } | null; onDone: () => void }) {
  const { palette } = useTheme();
  const opacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (!toast) return;
    opacity.setValue(0);
    const anim = Animated.sequence([
      Animated.timing(opacity, { toValue: 1, duration: 160, useNativeDriver: true }),
      Animated.delay(SHOW_MS),
      Animated.timing(opacity, { toValue: 0, duration: 220, useNativeDriver: true }),
    ]);
    anim.start(({ finished }) => finished && onDone());
    return () => anim.stop();
    // seq 가 바뀌면 같은 문구라도 다시 띄운다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [toast?.seq]);

  if (!toast) return null;
  return (
    <Animated.View
      pointerEvents="none"
      accessibilityLiveRegion="polite"
      style={[styles.wrap, { opacity, backgroundColor: palette.ink }]}
    >
      <Text style={[styles.text, { color: palette.paper }]}>{toast.text}</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    position: 'absolute',
    left: 24,
    right: 24,
    bottom: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 12,
  },
  text: {
    fontFamily: font.sans,
    fontSize: 13.5,
    lineHeight: 20,
    textAlign: 'center',
  },
});
