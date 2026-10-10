package com.cloudsql.lab.cloud;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;
@RestController
public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments) {this.payments=payments;}
    public record Verify(@Pattern(regexp="order_[A-Za-z0-9]+") @jakarta.validation.constraints.NotNull String orderId,
        @Pattern(regexp="pay_[A-Za-z0-9]+") @jakarta.validation.constraints.NotNull String paymentId,
        @Pattern(regexp="[a-fA-F0-9]{64}") @jakarta.validation.constraints.NotNull String signature) {}
    @PostMapping("/api/billing/orders") public PaymentService.Checkout order() {return payments.create();}
    @PostMapping("/api/billing/verify") public PaymentService.History verify(@Valid @RequestBody Verify input) {payments.verify(input.orderId(),input.paymentId(),input.signature());return payments.history(false);}
    @PostMapping("/api/billing/orders/{id}/refresh") public PaymentService.History refresh(@PathVariable String id) {payments.reconcile(id);return payments.history(false);}
    @GetMapping("/api/billing/payments") public PaymentService.History own() {return payments.history(false);}
    @GetMapping("/api/admin/payments") public PaymentService.History all() {return payments.history(true);}
}
