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
