import { createContext, useContext } from 'react'

export type ThemePreference = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

export interface ThemeState {
  preference: ThemePreference
  resolvedTheme: ResolvedTheme
  setPreference: (theme: ThemePreference) => void
}

export const ThemeContext = createContext<ThemeState | null>(null)

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used within ThemeProvider')
  return context
}
