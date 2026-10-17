package com.cloudsql.lab.auth;

import com.cloudsql.lab.TestAccounts;
import com.cloudsql.lab.cloud.AuditService;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class CloudSecurityIntegrationTest {
    @LocalServerPort int port;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditService audit;
    @Autowired ApiRateLimiter limiter;
    @Autowired PasswordHasher passwords;
    final HttpClient client=HttpClient.newHttpClient();
    final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
    Account user() { return auth.signup("Security demo",UUID.randomUUID()+"@example.test",TestAccounts.PASSWORD); }
    String session(Account user) {return "cloudsql_session="+auth.createSession(user);}
    HttpResponse<String> send(String method,String path,String cookie) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("X-CloudSQL-Request","1");
        if (cookie!=null) request.header("Cookie",cookie);
        return client.send(request.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void sessionsHideHashesEnforceOwnershipAndRevokeAccess() throws Exception {
        var owner=user(); var stranger=user(); String current=session(owner), other=session(owner), foreign=session(stranger);
        // Exercise backfill of a session created before the schema extension.
        jdbc.update("UPDATE platform.sessions SET session_id=NULL WHERE token_hash=?",AuthService.digest(other.split("=",2)[1]));
        var response=send("GET","/api/auth/sessions",current);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("token_hash",AuthService.digest(other.split("=",2)[1]),other.split("=",2)[1]);
        var rows=json.readTree(response.body());
        String otherId=null,currentId=null;
        for (var row:rows) if (row.get("current").asBoolean()) currentId=row.get("id").asString(); else otherId=row.get("id").asString();
        assertThat(otherId).isNotNull();
        assertThat(send("DELETE","/api/auth/sessions/"+otherId,foreign).statusCode()).isEqualTo(404);
        assertThat(send("DELETE","/api/auth/sessions/"+currentId,current).statusCode()).isEqualTo(404);
        assertThat(send("DELETE","/api/auth/sessions/"+otherId,current).statusCode()).isEqualTo(204);
        assertThat(send("GET","/api/auth/me",other).statusCode()).isEqualTo(401);
        assertThat(send("GET","/api/auth/me",current).statusCode()).isEqualTo(200);
        assertThat(send("GET","/api/admin/security",current).statusCode()).isEqualTo(403);
    }
    @Test void rateLimitIsAtomicAcrossInstancesAndExpires() throws Exception {
        String subject=UUID.randomUUID().toString();
        var first=new ApiRateLimiter(jdbc,audit,10); var second=new ApiRateLimiter(jdbc,audit,10);
        List<Callable<Boolean>> calls=new ArrayList<>();
        for (int i=0;i<30;i++) {var instance=i%2==0?first:second;calls.add(()->instance.allow(subject,null));}
        try (var executor=Executors.newFixedThreadPool(6)) {
            int allowed=0;for(var future:executor.invokeAll(calls)) if(future.get()) allowed++;
            assertThat(allowed).isEqualTo(10);
        }
        assertThat(jdbc.queryForObject("SELECT blocked FROM platform.api_limits WHERE subject=?",Integer.class,subject)).isEqualTo(20);
        jdbc.update("UPDATE platform.api_limits SET bucket_start=? WHERE subject=?",Timestamp.from(Instant.now().minus(2,ChronoUnit.MINUTES)),subject);
        assertThat(second.allow(subject,null)).isTrue();
    }
    @Test void httpLimitReturnsRetryAfterButLogoutStillWorks() throws Exception {
        var account=user(); String cookie=session(account);
        // Fill the current and next minute, so this assertion is stable at minute boundaries.
        var minute=Instant.now().truncatedTo(ChronoUnit.MINUTES);
        for(int i=0;i<2;i++) jdbc.update("INSERT INTO platform.api_limits (subject,bucket_start,requests,blocked) VALUES (?,?,?,0)",account.id(),Timestamp.from(minute.plus(i,ChronoUnit.MINUTES)),limiter.limit);
        var denied=send("GET","/api/auth/me",cookie);
        assertThat(denied.statusCode()).isEqualTo(429);
        assertThat(Integer.parseInt(denied.headers().firstValue("retry-after").orElseThrow())).isBetween(1,60);
        assertThat(send("POST","/api/auth/logout",cookie).statusCode()).isEqualTo(204);
    }
    @Test void loginLockIsReportedAndExpiresWithoutPermanentLockout() {
        var account=user();
        for(int i=0;i<10;i++) assertThatThrownBy(()->auth.login(account.email(),"wrong-password")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(401));
        assertThatThrownBy(()->auth.login(account.email(),TestAccounts.PASSWORD)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(429));
        jdbc.update("UPDATE platform.login_failures SET attempted_at=? WHERE email=?",Timestamp.from(Instant.now().minus(16,ChronoUnit.MINUTES)),account.email());
        assertThat(auth.login(account.email(),TestAccounts.PASSWORD).id()).isEqualTo(account.id());
    }
    @Test void onlyAdminSeesSecurityReportWithoutCredentials() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        new AdminProvisioner(jdbc,passwords,email,TestAccounts.PASSWORD).run(null);
        String cookie=session(auth.login(email,TestAccounts.PASSWORD));
        var response=send("GET","/api/admin/security",cookie);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("failedLogins","blockedLogins","rateLimitedRequests","activeSessions").doesNotContain(TestAccounts.PASSWORD,"token_hash","password_hash");
    }
}
