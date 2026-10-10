import type { ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';

/**
 * 아래에서 올라오는 시트. 바깥을 누르거나 Android 뒤로가기로 닫힌다.
 * 라이브러리 없이 Modal 로 만든다 — 메뉴 몇 개를 띄우는 데 제스처까지는 필요 없다.
 */
export function Sheet({
  visible,
  onClose,
  children,
}: {
  visible: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  const { palette } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose} statusBarTranslucent>
      <Pressable style={styles.backdrop} onPress={onClose} accessibilityLabel="닫기" />
      <View
        style={[
          styles.sheet,
          { backgroundColor: palette.surface, paddingBottom: Math.max(insets.bottom, 16) },
        ]}
      >
        <View style={[styles.handle, { backgroundColor: palette.rule }]} />
        {children}
      </View>
    </Modal>
  );
}

/** 시트 안의 한 줄 동작 */
export function SheetAction({
  label,
  onPress,
  tone = 'default',
}: {
  label: string;
  onPress: () => void;
  tone?: 'default' | 'danger';
}) {
  const { palette } = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.action, pressed && { backgroundColor: palette.ruleSoft }]}
    >
      <Text style={[styles.actionLabel, { color: tone === 'danger' ? palette.markers[1] : palette.ink }]}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(0,0,0,0.32)',
  },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    borderTopLeftRadius: 18,
    borderTopRightRadius: 18,
    paddingTop: 8,
  },
  handle: {
    alignSelf: 'center',
    width: 36,
    height: 4,
    borderRadius: 2,
    marginBottom: 8,
  },
  action: {
    minHeight: spacing.touchTarget + 8,
    paddingHorizontal: spacing.screenX + 4,
    justifyContent: 'center',
  },
  actionLabel: {
    fontFamily: font.sans,
    fontSize: 15.5,
  },
});
