import {
  GothicA1_400Regular,
  GothicA1_500Medium,
  GothicA1_600SemiBold,
  useFonts,
} from '@expo-google-fonts/gothic-a1';
import { IBMPlexMono_400Regular, IBMPlexMono_500Medium } from '@expo-google-fonts/ibm-plex-mono';
import { ActivityIndicator, View, useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
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

  if (!fontsLoaded) {
    return (
      <View style={{ flex: 1, backgroundColor: palette.paper, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator color={palette.muted} />
      </View>
    );
  }

  return (
    <SafeAreaProvider>
      <MainScreen />
    </SafeAreaProvider>
  );
}
