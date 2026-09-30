CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.user_problem_progress (
  user_id VARCHAR(100) NOT NULL,
  problem_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  solved_at TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (user_id, problem_id)
);

CREATE TABLE IF NOT EXISTS platform.database_workspaces (
  id VARCHAR(36) PRIMARY KEY,
  internal_workspace_id VARCHAR(36) NOT NULL UNIQUE,
  name VARCHAR(60) NOT NULL,
  owner_user_id VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  storage_used_bytes BIGINT NOT NULL DEFAULT 0,
  storage_limit_bytes BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_database_workspaces_owner
  ON platform.database_workspaces(owner_user_id);
