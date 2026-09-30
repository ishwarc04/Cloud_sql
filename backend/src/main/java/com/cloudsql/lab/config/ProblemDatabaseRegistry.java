package com.cloudsql.lab.config;

import java.sql.Connection;
import java.sql.SQLException;

public interface ProblemDatabaseRegistry {
    Connection getConnection(long problemId) throws SQLException;
}
