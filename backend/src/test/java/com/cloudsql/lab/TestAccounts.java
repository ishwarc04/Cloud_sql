package com.cloudsql.lab;

import java.net.URI;
import java.net.http.*;
import java.util.UUID;

public final class TestAccounts {
    public static final String PASSWORD = "test-password-strong-2026";
    private TestAccounts() { }
    public static String signup(int port) throws Exception {
        return authenticate(port, "signup", "{\"name\":\"Test Learner\",\"email\":\"" + UUID.randomUUID() + "@example.test\",\"password\":\"" + PASSWORD + "\"}");
    }
    public static String login(int port, String email) throws Exception {
        return authenticate(port, "login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
    }
    private static String authenticate(int port, String action, String body) throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/" + action))
                .header("Content-Type", "application/json").header("X-CloudSQL-Request", "1")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) throw new IllegalStateException(response.body());
        return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }
}
