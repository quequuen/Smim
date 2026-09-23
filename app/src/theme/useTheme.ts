import { useColorScheme } from 'react-native';
import { darkPalette, lightPalette, type Palette } from './tokens';

export function useTheme(): { palette: Palette; isDark: boolean } {
  const isDark = useColorScheme() === 'dark';
  return { palette: isDark ? darkPalette : lightPalette, isDark };
}
