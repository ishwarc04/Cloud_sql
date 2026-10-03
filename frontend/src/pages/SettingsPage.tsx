import { useTheme, type ThemePreference } from '../app/theme/ThemeContext'

const themes: Array<{ value: ThemePreference; label: string; description: string }> = [
  { value: 'system', label: 'System', description: 'Follow device setting' },
  { value: 'light', label: 'Light', description: 'Bright console theme' },
  { value: 'dark', label: 'Dark', description: 'Low-light console theme' },
]

export function SettingsPage() {
  const { preference, resolvedTheme, setPreference } = useTheme()
  return <section className="page settings-page">
    <header className="page-header"><div><span className="eyebrow">Workspace preferences</span><h1>Settings</h1><p>Control how CloudSQL Lab is displayed on this device.</p></div></header>
    <div className="settings-sections">
      <section className="settings-section">
        <div className="settings-copy"><h2>Appearance</h2><p>Choose how CloudSQL Lab is displayed on this device. System currently resolves to {resolvedTheme}.</p></div>
        <div className="option-grid" aria-label="Theme preference">
          {themes.map((theme) => <button key={theme.value} className={`option-card ${preference === theme.value ? 'is-selected' : ''}`} onClick={() => setPreference(theme.value)} type="button"><span className={`theme-swatch theme-${theme.value}`} /><strong>{theme.label}</strong><small>{theme.description}</small></button>)}
        </div>
      </section>
    </div>
  </section>
}
