package com.cloudsql.lab.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final boolean secure;
    private final String sameSite;
    public AuthController(AuthService service, @Value("${cloudsql.auth.cookie-secure:true}") boolean secure,
            @Value("${cloudsql.auth.cookie-same-site:Lax}") String sameSite) {
        this.service = service;
        this.secure = secure;
        this.sameSite = sameSite;
        if (!java.util.Set.of("Lax", "Strict", "None").contains(sameSite) || (!secure && sameSite.equals("None")))
            throw new IllegalArgumentException("Invalid session cookie configuration");
    }
    public record Signup(@NotBlank @Size(max=80) String name, @NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(min=12, max=128) String password) { }
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=128) String password) { }

    @PostMapping("/signup") @ResponseStatus(HttpStatus.CREATED)
    public Account signup(@Valid @RequestBody Signup input, HttpServletRequest request, HttpServletResponse response) {
        Account account = service.signup(input.name(), input.email(), input.password());
        request.setAttribute("account", account);
        service.revoke(SessionFilter.token(request));
        cookie(response, service.createSession(account), service.sessionSeconds());
        return account;
    }
    @PostMapping("/login")
    public Account login(@Valid @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        Account account = service.login(input.email(), input.password());
        request.setAttribute("account", account);
        service.revoke(SessionFilter.token(request));
        cookie(response, service.createSession(account), service.sessionSeconds());
        return account;
    }
    @GetMapping("/me") public Account me(HttpServletRequest request) { return (Account) request.getAttribute("account"); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        service.revoke(SessionFilter.token(request));
        cookie(response, "", 0);
    }
    private void cookie(HttpServletResponse response, String token, long seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("cloudsql_session", token).httpOnly(true)
                .secure(secure).sameSite(sameSite).path("/").maxAge(seconds).build().toString());
    }
}
