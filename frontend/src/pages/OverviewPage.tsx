import type { IconName } from '../components/ui/Icon'
import { Icon } from '../components/ui/Icon'

interface PreviewSection {
  title: string
  description: string
  icon: IconName
}

const pageContent = {
  dashboard: {
    eyebrow: 'Workspace overview', title: 'Dashboard', description: 'A concise view of your SQL learning and cloud database workspace.', action: 'Customize',
    sections: [
      { title: 'Learning activity', description: 'Progress, recent problems, and active practice sessions.', icon: 'practice' },
      { title: 'Database resources', description: 'Instances, storage allocation, and recent connections.', icon: 'database' },
      { title: 'Workspace notices', description: 'Quota, platform, and account-level updates.', icon: 'activity' },
    ],
  },
  practice: {
    eyebrow: 'SQL learning', title: 'SQL Practice', description: 'Interview-focused SQL problems organized by topic and difficulty.', action: 'Browse problems',
    sections: [
      { title: 'Problem library', description: 'Search, difficulty filters, and completion status.', icon: 'practice' },
      { title: 'Study tracks', description: 'Structured paths for joins, aggregation, and analytics.', icon: 'terminal' },
    ],
  },
  databases: {
    eyebrow: 'Cloud workspace', title: 'My Databases', description: 'Create and manage isolated SQL environments within your resource quota.', action: 'Create database',
    sections: [
      { title: 'Database instances', description: 'Name, engine, region, state, and storage usage.', icon: 'database' },
      { title: 'Resource allocation', description: 'Current storage and compute quota at a glance.', icon: 'server' },
    ],
  },
  admin: {
    eyebrow: 'Platform operations', title: 'Admin Dashboard', description: 'Administration workspace for service health, usage, and policy.', action: 'View activity',
    sections: [
      { title: 'Service health', description: 'Infrastructure status and operational signals.', icon: 'activity' },
      { title: 'Users and resources', description: 'Account activity, database allocation, and quotas.', icon: 'users' },
      { title: 'Platform events', description: 'Query activity, errors, and audit-ready events.', icon: 'terminal' },
    ],
  },
} satisfies Record<string, { eyebrow: string; title: string; description: string; action: string; sections: PreviewSection[] }>

export type OverviewPageKind = keyof typeof pageContent

export function OverviewPage({ kind }: { kind: OverviewPageKind }) {
  const content = pageContent[kind]
  return <section className="page">
    <header className="page-header">
      <div><span className="eyebrow">{content.eyebrow}</span><h1>{content.title}</h1><p>{content.description}</p></div>
      <button className="primary-button" type="button" disabled>{content.action}</button>
    </header>
    <div className="section-list" aria-label={`${content.title} planned sections`}>
      <div className="section-list-header"><span>Planned workspace</span><span>Phase preview</span></div>
      {content.sections.map((section) => <div className="section-row" key={section.title}>
        <span className="section-icon"><Icon name={section.icon} /></span>
        <div><strong>{section.title}</strong><p>{section.description}</p></div>
        <span className="pending-label">Not configured</span><Icon name="chevron" className="row-chevron" />
      </div>)}
    </div>
  </section>
}
