package com.cloudsql.lab.database;

import com.cloudsql.lab.database.model.DatabaseWorkspace;
import java.sql.*;
import javax.sql.DataSource;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PostgresQuotaTest {
    private DatabaseWorkspace workspace() { return new DatabaseWorkspace("public-id", "a".repeat(32), "Quota Test", "owner", "ACTIVE", OffsetDateTime.now(), 100, 200); }
    @Test void overQuotaWriteRollsBackBeforeCommit() throws Exception {
        DataSource source = mock(DataSource.class); Connection before = mock(Connection.class), connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(before, connection);
        PreparedStatement sizeBefore = mock(PreparedStatement.class), sizeAfter = mock(PreparedStatement.class);
        ResultSet oldSize = mock(ResultSet.class), newSize = mock(ResultSet.class);
        when(before.prepareStatement(anyString())).thenReturn(sizeBefore); when(sizeBefore.executeQuery()).thenReturn(oldSize); when(oldSize.next()).thenReturn(true); when(oldSize.getLong(1)).thenReturn(100L);
        when(connection.prepareStatement(anyString())).thenReturn(sizeAfter); when(sizeAfter.executeQuery()).thenReturn(newSize); when(newSize.next()).thenReturn(true); when(newSize.getLong(1)).thenReturn(201L);
        Statement scope = mock(Statement.class), query = mock(Statement.class); when(connection.createStatement()).thenReturn(scope, query);
        var engine = new PostgresWorkspaceEngine(source, new WorkspaceProperties(2, 200, 5, 200, "unused"));
        assertThatThrownBy(() -> engine.execute(workspace(), "INSERT INTO cargo VALUES (1)", "INSERT")).isInstanceOf(WorkspaceQuotaException.class);
        verify(connection).rollback(); verify(connection, never()).commit();
    }
    @Test void engineReusesOuterTransactionWithoutCommittingItEarly() throws Exception {
        DataSource source = mock(DataSource.class); Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        Statement statement = mock(Statement.class); when(connection.createStatement()).thenReturn(statement);
        PreparedStatement size = mock(PreparedStatement.class); ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(size); when(size.executeQuery()).thenReturn(result); when(result.next()).thenReturn(true); when(result.getLong(1)).thenReturn(100L);
        var engine = new PostgresWorkspaceEngine(source, new WorkspaceProperties(2, 200, 5, 200, "unused"));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> { engine.execute(workspace(), "INSERT INTO cargo VALUES (1)", "INSERT"); try { verify(connection, never()).commit(); } catch (SQLException exception) { throw new RuntimeException(exception); } });
        verify(source, times(1)).getConnection(); verify(connection, times(1)).commit();
    }
}
