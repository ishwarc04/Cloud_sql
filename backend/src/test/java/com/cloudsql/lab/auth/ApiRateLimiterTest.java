package com.cloudsql.lab.auth;

import com.cloudsql.lab.cloud.AuditService;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiRateLimiterTest {
    @Test void requestRetriesWhenAnotherInstanceCreatesCounterBetweenUpdateAndLookup() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        AuditService audit=mock(AuditService.class);
        String increment="UPDATE platform.api_limits SET requests=requests+1 WHERE subject=? AND bucket_start=? AND requests < ?";
        // Initial row is absent; another request inserts it before this request's SELECT.
        when(jdbc.update(eq(increment),eq("learner"),any(Timestamp.class),eq(120))).thenReturn(0,1);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("learner"),any(Timestamp.class))).thenReturn(1);
        assertThat(new ApiRateLimiter(jdbc,audit,120).allow("learner","learner")).isTrue();
        verify(jdbc,times(2)).update(eq(increment),eq("learner"),any(Timestamp.class),eq(120));
        verifyNoInteractions(audit);
    }
}
