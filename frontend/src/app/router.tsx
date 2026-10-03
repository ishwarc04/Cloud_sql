import { Navigate, createBrowserRouter } from 'react-router-dom'
import { AppShell } from '../components/layout/AppShell'
import { RequireRole } from './auth/RequireRole'
import { RequireAuth } from './auth/RequireAuth'
import { AuthPage } from '../pages/AuthPage'
import { UserDashboardPage } from '../pages/UserDashboardPage'
import { SettingsPage } from '../pages/SettingsPage'
import { ProblemListPage } from '../pages/ProblemListPage'
import { ProblemDetailPage } from '../pages/ProblemDetailPage'
import { DatabaseListPage } from '../pages/DatabaseListPage'
import { DatabaseWorkspacePage } from '../pages/DatabaseWorkspacePage'
import { AdminDashboardPage } from '../pages/AdminDashboardPage'
import { RoleHome } from './auth/RoleHome'
import { LeaderboardPage } from '../pages/LeaderboardPage'
import { BillingPage } from '../pages/BillingPage'

export const router = createBrowserRouter([
  { path: '/login', element: <AuthPage key="login" mode="login" /> },
  { path: '/signup', element: <AuthPage key="signup" mode="signup" /> },
  {
    path: '/', element: <RequireAuth><AppShell /></RequireAuth>, children: [
      { index: true, element: <RoleHome /> },
      { path: 'dashboard', element: <RequireRole role="user"><UserDashboardPage /></RequireRole> },
      { path: 'leaderboard', element: <RequireRole role="user"><LeaderboardPage /></RequireRole> },
      { path: 'billing', element: <RequireRole role="user"><BillingPage /></RequireRole> },
      { path: 'practice', element: <RequireRole role="user"><ProblemListPage /></RequireRole> },
      { path: 'practice/:problemId', element: <RequireRole role="user"><ProblemDetailPage /></RequireRole> },
      { path: 'databases', element: <RequireRole role="user"><DatabaseListPage /></RequireRole> },
      { path: 'databases/:databaseId', element: <RequireRole role="user"><DatabaseWorkspacePage /></RequireRole> },
      { path: 'admin', element: <RequireRole role="admin"><AdminDashboardPage /></RequireRole> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/dashboard" replace /> },
])
