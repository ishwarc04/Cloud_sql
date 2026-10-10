package com.cloudsql.lab.cloud;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.cloudsql.lab.progress.CurrentUserProvider;
import com.cloudsql.lab.database.WorkspaceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class PaymentService {
    private final JdbcTemplate jdbc;
    private final CurrentUserProvider current;
    private final RazorpayGateway gateway;
    private final WorkspaceProperties defaults;
    private final AuditService audit;
    public PaymentService(JdbcTemplate jdbc, CurrentUserProvider current, RazorpayGateway gateway, WorkspaceProperties defaults, AuditService audit) {
        this.jdbc=jdbc;this.current=current;this.gateway=gateway;this.defaults=defaults;this.audit=audit;
    }
    public record Checkout(String orderId, String keyId, int amount, String currency) {}
    public record Receipt(String orderId,String paymentId,String userName,String email,int amountPaise,String currency,String status,Instant createdAt,Instant paidAt) {}
    public record History(List<Receipt> payments, boolean configured, boolean testMode) {}
    private void lock(String user) { jdbc.queryForObject("SELECT id FROM platform.users WHERE id = ? FOR UPDATE",String.class,user); }
    @Transactional
    public Checkout create() {
        String user=current.currentUserId(); lock(user);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM platform.account_plans WHERE user_id = ?",Integer.class,user)>0) throw new ResponseStatusException(CONFLICT,"Pro is already active.");
        var pending=jdbc.queryForList("SELECT order_id FROM platform.payment_orders WHERE user_id = ? AND status <> 'PAID' ORDER BY created_at DESC",String.class,user);
        if (!pending.isEmpty()) return new Checkout(pending.getFirst(),gateway.publicKey(),19900,"INR");
        var order=gateway.order(UUID.randomUUID().toString());
        String id=String.valueOf(order.get("id"));
        if (!id.matches("order_[A-Za-z0-9]+") || !(order.get("amount") instanceof Number amount) || amount.intValue()!=19900 || !"INR".equals(order.get("currency"))) throw new ResponseStatusException(BAD_GATEWAY,"Invalid provider order.");
        jdbc.update("INSERT INTO platform.payment_orders (order_id,user_id,amount_paise,currency,status,created_at) VALUES (?, ?,19900,'INR','CREATED',?)",id,user,Timestamp.from(Instant.now()));
        return new Checkout(id,gateway.publicKey(),19900,"INR");
    }
    @Transactional
    public void verify(String order,String payment,String signature) {
        String user=current.currentUserId();lock(user);requireOwner(order,user);
        if (!gateway.signatureValid(order,payment,signature)) throw new ResponseStatusException(BAD_REQUEST,"Payment signature could not be verified.");
        confirm(order,user,gateway.payment(payment));
    }
    private void requireOwner(String order,String user) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM platform.payment_orders WHERE order_id = ? AND user_id = ?",Integer.class,order,user)!=1) throw new ResponseStatusException(NOT_FOUND,"Payment order not found.");
    }
    private void confirm(String order,String user,Map<String,Object> p) {
        if (!order.equals(p.get("order_id")) || !(p.get("amount") instanceof Number n) || n.intValue()!=19900 || !"INR".equals(p.get("currency"))) throw new ResponseStatusException(BAD_REQUEST,"Payment does not match the order.");
        if (!"captured".equals(p.get("status"))) throw new ResponseStatusException(CONFLICT,"Payment is not captured yet. Refresh payment history to check again.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM platform.payment_orders WHERE order_id = ? AND status = 'PAID'",Integer.class,order)>0) return;
        var now=Timestamp.from(Instant.now());
        jdbc.update("UPDATE platform.payment_orders SET payment_id=?,status='PAID',paid_at=? WHERE order_id=?",p.get("id"),now,order);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM platform.account_plans WHERE user_id=?",Integer.class,user)==0) jdbc.update("INSERT INTO platform.account_plans VALUES (?,'PRO',?)",user,now);
        long limit=Math.max(50L*1024*1024,defaults.storageLimitBytes());
        jdbc.update("UPDATE platform.database_workspaces SET storage_limit_bytes=CASE WHEN storage_limit_bytes < ? THEN ? ELSE storage_limit_bytes END WHERE owner_user_id=?",limit,limit,user);
        audit.recordInTransaction(user,"RAZORPAY_TEST_PAYMENT",order,"PAID");
    }
    @Transactional
    public void reconcile(String order) {
        String user=current.currentUserId();lock(user);requireOwner(order,user);
        var payload=gateway.orderPayments(order);
        Map<String,Object> lastAttempt=null;
        if (payload.get("items") instanceof List<?> items) for (Object item:items) if (item instanceof Map<?,?> raw) {
            @SuppressWarnings("unchecked") var payment=(Map<String,Object>)raw;
            if ("captured".equals(payment.get("status"))) {confirm(order,user,payment);return;}
            if (lastAttempt==null) lastAttempt=payment;
        }
        if (lastAttempt!=null && order.equals(lastAttempt.get("order_id"))) {
            String status="failed".equals(lastAttempt.get("status"))?"FAILED":"AUTHORIZED";
            jdbc.update("UPDATE platform.payment_orders SET status=? WHERE order_id=? AND status<>'PAID'",status,order);
        }
    }
    public History history(boolean admin) {
        String sql="SELECT o.*, u.name, u.email FROM platform.payment_orders o JOIN platform.users u ON u.id=o.user_id "+(admin?"":"WHERE o.user_id=? ")+"ORDER BY o.created_at DESC LIMIT 100";
        Object[] args=admin?new Object[]{}:new Object[]{current.currentUserId()};
        return new History(jdbc.query(sql,(r,i)->new Receipt(r.getString("order_id"),r.getString("payment_id"),r.getString("name"),r.getString("email"),r.getInt("amount_paise"),r.getString("currency"),r.getString("status"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("paid_at")==null?null:r.getTimestamp("paid_at").toInstant()),args),gateway.configured(),true);
    }
}
