import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ThemeContext, type ResolvedTheme, type ThemePreference, type ThemeState } from './ThemeContext'

const THEME_STORAGE_KEY = 'cloudsql-lab:theme'

function getInitialPreference(): ThemePreference {
  const stored = localStorage.getItem(THEME_STORAGE_KEY)
  return stored === 'light' || stored === 'dark' ? stored : 'system'
}

function getSystemTheme(): ResolvedTheme {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(getInitialPreference)
  const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(getSystemTheme)
  const resolvedTheme = preference === 'system' ? systemTheme : preference

  useEffect(() => {
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    const syncSystemTheme = () => setSystemTheme(query.matches ? 'dark' : 'light')
    query.addEventListener('change', syncSystemTheme)
    return () => query.removeEventListener('change', syncSystemTheme)
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme
    document.documentElement.style.colorScheme = resolvedTheme
  }, [resolvedTheme])

  const value = useMemo<ThemeState>(() => ({
    preference,
    resolvedTheme,
    setPreference: (nextTheme) => {
      localStorage.setItem(THEME_STORAGE_KEY, nextTheme)
      setPreferenceState(nextTheme)
    },
  }), [preference, resolvedTheme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
