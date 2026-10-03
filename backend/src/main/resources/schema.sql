CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.users (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(256) NOT NULL,
  role VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS platform.sessions (
  token_hash VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES platform.users(id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON platform.sessions(expires_at);
CREATE TABLE IF NOT EXISTS platform.login_failures (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  attempted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_login_failures ON platform.login_failures(email, attempted_at);
CREATE TABLE IF NOT EXISTS platform.submissions (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES platform.users(id) ON DELETE CASCADE,
  problem_id BIGINT NOT NULL,
  query_text VARCHAR(20000) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  points_awarded INTEGER NOT NULL DEFAULT 0,
  submitted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_submissions_user ON platform.submissions(user_id, submitted_at);
CREATE INDEX IF NOT EXISTS idx_submissions_time ON platform.submissions(submitted_at);

CREATE TABLE IF NOT EXISTS platform.user_problem_progress (
  user_id VARCHAR(100) NOT NULL,
  problem_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  solved_at TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (user_id, problem_id)
);

ALTER TABLE platform.user_problem_progress ADD COLUMN IF NOT EXISTS points INTEGER NOT NULL DEFAULT 0;

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
