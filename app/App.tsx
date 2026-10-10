import {
  GothicA1_400Regular,
  GothicA1_500Medium,
  GothicA1_600SemiBold,
  useFonts,
} from '@expo-google-fonts/gothic-a1';
import { IBMPlexMono_400Regular, IBMPlexMono_500Medium } from '@expo-google-fonts/ibm-plex-mono';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View, useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { TERMS_VERSION } from './src/content/terms';
import { getItem, KEYS, setItem } from './src/lib/storage';
import { AgreementScreen } from './src/screens/AgreementScreen';
import { MainScreen } from './src/screens/MainScreen';
import { darkPalette, lightPalette } from './src/theme/tokens';

export default function App() {
  const isDark = useColorScheme() === 'dark';
  const palette = isDark ? darkPalette : lightPalette;

  const [fontsLoaded] = useFonts({
    GothicA1_400Regular,
    GothicA1_500Medium,
    GothicA1_600SemiBold,
    IBMPlexMono_400Regular,
    IBMPlexMono_500Medium,
  });

  // 약관이 바뀌면(VERSION) 기존 사용자도 다시 동의한다
  const [agreed, setAgreed] = useState<boolean | null>(null);
  useEffect(() => {
    getItem(KEYS.termsAgreed).then((v) => setAgreed(v === TERMS_VERSION));
  }, []);

  const agree = () => {
    setAgreed(true);
    setItem(KEYS.termsAgreed, TERMS_VERSION);
  };

  if (!fontsLoaded || agreed === null) {
    return (
      <View style={{ flex: 1, backgroundColor: palette.paper, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator color={palette.muted} />
      </View>
    );
  }

  return (
    <SafeAreaProvider>
      {agreed ? <MainScreen /> : <AgreementScreen onAgree={agree} />}
    </SafeAreaProvider>
  );
}
