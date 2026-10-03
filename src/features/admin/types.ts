export interface AdminOverview {
  totalDatabaseWorkspaces: number
  activeDatabaseWorkspaces: number
  totalStorageUsedBytes: number
  totalStorageCapacityBytes: number
  availableStorageBytes: number
  totalPracticeProblems: number
  solvedProblemCount: number
  totalPracticeAttempts: number
  serviceStatus: string
  generatedAt: string
}

export interface AdminDatabase {
  id: string
  name: string
  ownerUserId: string
  status: string
  createdAt: string
  storageUsedBytes: number
  storageLimitBytes: number
}

export interface AdminUser {
  id: string; name: string; email: string; role: 'USER' | 'ADMIN'; createdAt: string
  solvedProblems: number; attempts: number; points: number; databaseCount: number; storageUsedBytes: number
  activeSessions: number; lastSubmissionAt: string | null
}
export interface AdminWorkspace {
  id: string; name: string; ownerUserId: string; ownerName: string; ownerEmail: string | null
  status: string; createdAt: string; storageUsedBytes: number; storageLimitBytes: number
}
export interface AdminProblem {
  id: number; title: string; difficulty: 'Easy' | 'Medium' | 'Hard'; category: string
  attempts: number; accepted: number; errors: number; solvedUsers: number
}
export interface AdminSubmission {
  id: string; userId: string; userName: string; problemId: number; problemTitle: string; difficulty: string
  category: string; outcome: 'ACCEPTED' | 'WRONG_ANSWER' | 'ERROR'; pointsAwarded: number; submittedAt: string
}
export interface DailyActivity { date: string; submissions: number; accepted: number; errors: number; registrations: number }
export interface AdminAnalytics {
  summary: {
    totalUsers: number; learners: number; administrators: number; activeUsersSevenDays: number; activeSessions: number
    totalSubmissions: number; acceptedSubmissions: number; wrongAnswers: number; queryErrors: number
    solvedUserProblems: number; totalPoints: number; totalWorkspaces: number; storageUsedBytes: number
    storageAllocatedBytes: number; databaseLatencyMs: number
  }
  dailyActivity: DailyActivity[]; users: AdminUser[]; workspaces: AdminWorkspace[]; problems: AdminProblem[]
  recentSubmissions: AdminSubmission[]
  quotas: { maxDatabasesPerUser: number; storageLimitPerDatabaseBytes: number; queryTimeoutSeconds: number; maxReturnedRows: number }
  generatedAt: string
}
export interface AdminUserDetail { user: AdminUser; workspaces: AdminWorkspace[]; recentSubmissions: AdminSubmission[] }
