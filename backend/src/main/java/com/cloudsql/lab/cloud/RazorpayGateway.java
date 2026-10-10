package com.cloudsql.lab.cloud;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Component
public class RazorpayGateway {
    private final String key, secret;
    private final RestClient client;
    public RazorpayGateway(@Value("${RAZORPAY_KEY_ID:}") String key, @Value("${RAZORPAY_KEY_SECRET:}") String secret) {
        this.key = key; this.secret = secret;
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        client = RestClient.builder().baseUrl("https://api.razorpay.com/v1").requestFactory(factory).defaultHeaders(h -> h.setBasicAuth(key, secret)).build();
    }
    public boolean configured() { return key.startsWith("rzp_test_") && !secret.isBlank(); }
    public String publicKey() { requireConfigured(); return key; }
    private void requireConfigured() { if (!configured()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Razorpay test checkout is not configured."); }
    @SuppressWarnings("unchecked")
    public Map<String,Object> order(String receipt) {
        requireConfigured();
        try { return client.post().uri("/orders").body(Map.of("amount",19900,"currency","INR","receipt",receipt)).retrieve().body(Map.class); }
        catch (RuntimeException e) { throw new ResponseStatusException(BAD_GATEWAY,"Payment provider is unavailable. Please retry."); }
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> payment(String id) {
        requireConfigured();
        try { return client.get().uri("/payments/{id}",id).retrieve().body(Map.class); }
        catch (RuntimeException e) { throw new ResponseStatusException(BAD_GATEWAY,"Unable to confirm payment. Refresh payment history to retry."); }
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> orderPayments(String id) {
        requireConfigured();
        try { return client.get().uri("/orders/{id}/payments",id).retrieve().body(Map.class); }
        catch (RuntimeException e) { throw new ResponseStatusException(BAD_GATEWAY,"Unable to check payment status. Please retry."); }
    }
    public boolean signatureValid(String order, String payment, String signature) {
        requireConfigured();
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal((order+"|"+payment).getBytes(StandardCharsets.UTF_8)),HexFormat.of().parseHex(signature));
        } catch (IllegalArgumentException e) { return false; }
        catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Signature verification unavailable",e); }
    }
}
