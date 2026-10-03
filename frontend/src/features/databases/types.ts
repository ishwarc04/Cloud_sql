export interface DatabaseWorkspace {
  id: string
  name: string
  status: string
  createdAt: string
  storageUsedBytes: number
  storageLimitBytes: number
}

export interface DatabaseQuota {
  databaseCount: number
  maximumDatabases: number
  totalStorageUsedBytes: number
  totalStorageLimitBytes: number
}

export interface DatabaseListResponse {
  databases: DatabaseWorkspace[]
  quota: DatabaseQuota
}

export interface WorkspaceColumn {
  name: string
  dataType: string
  nullable: boolean
}

export interface WorkspaceTable {
  name: string
  columns: WorkspaceColumn[]
}

export interface WorkspaceSchema {
  tables: WorkspaceTable[]
}

export interface WorkspaceQueryResult {
  columns: string[]
  rows: unknown[][]
  affectedRows: number
  statementType: string | null
  executionTimeMs: number
  error: string | null
}
