import { Navigate, createBrowserRouter } from 'react-router-dom'
import { AppShell } from '../components/layout/AppShell'
import { RequireRole } from './auth/RequireRole'
import { OverviewPage } from '../pages/OverviewPage'
import { SettingsPage } from '../pages/SettingsPage'
import { ProblemListPage } from '../pages/ProblemListPage'
import { ProblemDetailPage } from '../pages/ProblemDetailPage'
import { DatabaseListPage } from '../pages/DatabaseListPage'
import { DatabaseWorkspacePage } from '../pages/DatabaseWorkspacePage'
import { AdminDashboardPage } from '../pages/AdminDashboardPage'

export const router = createBrowserRouter([
  {
    path: '/', element: <AppShell />, children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: <OverviewPage kind="dashboard" /> },
      { path: 'practice', element: <ProblemListPage /> },
      { path: 'practice/:problemId', element: <ProblemDetailPage /> },
      { path: 'databases', element: <DatabaseListPage /> },
      { path: 'databases/:databaseId', element: <DatabaseWorkspacePage /> },
      { path: 'admin', element: <RequireRole role="admin"><AdminDashboardPage /></RequireRole> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/dashboard" replace /> },
])
