package com.cloudsql.lab.cloud;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;
class RazorpaySignatureTest {
    @Test void verifiesHmacAndRejectsTamperingAndLiveKeys() throws Exception {
        var gateway=new RazorpayGateway("rzp_test_example","test-secret");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal("order_demo|pay_demo".getBytes(StandardCharsets.UTF_8)));
        assertThat(gateway.signatureValid("order_demo","pay_demo",signature)).isTrue();
        assertThat(gateway.signatureValid("order_other","pay_demo",signature)).isFalse();
        assertThat(gateway.signatureValid("order_demo","pay_demo","invalid")).isFalse();
        assertThat(new RazorpayGateway("rzp_live_example","secret").configured()).isFalse();
    }
}
