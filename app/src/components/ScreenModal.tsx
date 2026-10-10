import type { ReactNode } from 'react';
import { Modal, Pressable, StatusBar, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { font, spacing } from '../theme/tokens';
import { useTheme } from '../theme/useTheme';
import { BackIcon, CloseIcon } from './Icons';

/**
 * 메인 위로 올라오는 전체 화면. 답글 목록·설정처럼 메인으로 돌아오는 화면에 쓴다.
 * 화면이 몇 개 안 되므로 내비게이션 라이브러리 대신 Modal 로 쌓는다.
 * Android 뒤로가기는 onRequestClose 로 받는다.
 */
export function ScreenModal({
  visible,
  title,
  onClose,
  onBack,
  children,
}: {
  visible: boolean;
  title: string;
  onClose: () => void;
  /** 있으면 닫기 대신 뒤로가기 화살표를 보여준다 (화면 안에서 한 단계 들어갔을 때) */
  onBack?: () => void;
  children: ReactNode;
}) {
  const { palette, isDark } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onBack ?? onClose} statusBarTranslucent>
      <View style={[styles.root, { backgroundColor: palette.paper, paddingTop: insets.top }]}>
        <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />
        <View style={[styles.header, { borderBottomColor: palette.ruleSoft }]}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={onBack ? '뒤로' : '닫기'}
            onPress={onBack ?? onClose}
            style={styles.iconButton}
          >
            {onBack ? <BackIcon color={palette.ink2} /> : <CloseIcon color={palette.ink2} size={18} />}
          </Pressable>
          <Text style={[styles.title, { color: palette.ink }]}>{title}</Text>
          <View style={styles.iconButton} />
        </View>
        <View style={[styles.body, { paddingBottom: insets.bottom }]}>{children}</View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    height: spacing.headerHeight,
    paddingHorizontal: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  title: {
    fontFamily: font.sansSemiBold,
    fontSize: 15.5,
  },
  iconButton: {
    width: spacing.touchTarget,
    height: spacing.touchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
  body: { flex: 1 },
});
