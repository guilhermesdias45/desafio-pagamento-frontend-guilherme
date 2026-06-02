package com.acaboumony.fraud;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.rules.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for each deterministic fraud rule.
 * No Spring context required — rules are pure functions.
 */
class FraudRulesTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private FraudAnalysisRequest buildRequest(long amountCents, String fingerprint) {
        return new FraudAnalysisRequest(
                "txn-001", CUSTOMER_ID, amountCents,
                "pm_card_visa", "192.168.1.100",
                fingerprint, null, null
        );
    }

    private FraudRuleContext baseContext() {
        return new FraudRuleContext(0L, 1000L, false, 0L, false);
    }

    // -------------------------------------------------------------------------
    // VelocityRule
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("VelocityRule")
    class VelocityRuleTests {

        private final VelocityRule rule = new VelocityRule();

        @Test
        @DisplayName("returns 0 when velocity count is below threshold")
        void nao_dispara_quando_velocidade_abaixo_do_limite() {
            FraudRuleContext ctx = new FraudRuleContext(2L, 0L, false, 0L, false);
            assertThat(rule.evaluate(buildRequest(100L, null), ctx)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 30 when velocity count equals threshold (exactly 3)")
        void dispara_quando_velocidade_igual_ao_limite() {
            FraudRuleContext ctx = new FraudRuleContext(3L, 0L, false, 0L, false);
            assertThat(rule.evaluate(buildRequest(100L, null), ctx)).isEqualTo(30);
        }

        @Test
        @DisplayName("returns 30 when velocity count exceeds threshold")
        void dispara_quando_velocidade_acima_do_limite() {
            FraudRuleContext ctx = new FraudRuleContext(10L, 0L, false, 0L, false);
            assertThat(rule.evaluate(buildRequest(100L, null), ctx)).isEqualTo(30);
        }

        @Test
        @DisplayName("rule ID is VELOCITY_EXCEEDED")
        void rule_id_correto() {
            assertThat(rule.getRuleId()).isEqualTo("VELOCITY_EXCEEDED");
        }
    }

    // -------------------------------------------------------------------------
    // AmountAnomalyRule
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("AmountAnomalyRule")
    class AmountAnomalyRuleTests {

        private final AmountAnomalyRule rule = new AmountAnomalyRule();

        @Test
        @DisplayName("skips when average is 0 (new customer)")
        void nao_dispara_quando_media_zero() {
            FraudRuleContext ctx = new FraudRuleContext(0L, 0L, false, 0L, false);
            assertThat(rule.evaluate(buildRequest(999_999L, null), ctx)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when amount is exactly 5x average (not over)")
        void nao_dispara_quando_valor_exatamente_cinco_vezes_media() {
            FraudRuleContext ctx = new FraudRuleContext(0L, 1000L, false, 0L, false);
            // 5000 == 5 * 1000, not GREATER than
            assertThat(rule.evaluate(buildRequest(5_000L, null), ctx)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 25 when amount is more than 5x average")
        void dispara_quando_valor_maior_que_cinco_vezes_media() {
            FraudRuleContext ctx = new FraudRuleContext(0L, 1000L, false, 0L, false);
            assertThat(rule.evaluate(buildRequest(5_001L, null), ctx)).isEqualTo(25);
        }

        @Test
        @DisplayName("rule ID is AMOUNT_ANOMALY")
        void rule_id_correto() {
            assertThat(rule.getRuleId()).isEqualTo("AMOUNT_ANOMALY");
        }
    }

    // -------------------------------------------------------------------------
    // IpBlacklistRule
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("IpBlacklistRule")
    class IpBlacklistRuleTests {

        private final IpBlacklistRule rule = new IpBlacklistRule();

        @Test
        @DisplayName("returns 0 when IP is not blacklisted")
        void nao_dispara_quando_ip_nao_esta_na_lista() {
            FraudRuleContext ctx = new FraudRuleContext(0L, 0L, false, 0L, false);
            assertThat(rule.evaluate(buildRequest(100L, null), ctx)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 40 when IP is blacklisted")
        void dispara_quando_ip_esta_na_lista() {
            FraudRuleContext ctx = new FraudRuleContext(0L, 0L, true, 0L, false);
            assertThat(rule.evaluate(buildRequest(100L, null), ctx)).isEqualTo(40);
        }

        @Test
        @DisplayName("rule ID is IP_BLACKLISTED")
        void rule_id_correto() {
            assertThat(rule.getRuleId()).isEqualTo("IP_BLACKLISTED");
        }
    }

    // -------------------------------------------------------------------------
    // NewDeviceHighValueRule
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("NewDeviceHighValueRule")
    class NewDeviceHighValueRuleTests {

        private final NewDeviceHighValueRule rule = new NewDeviceHighValueRule();

        @Test
        @DisplayName("returns 0 when device fingerprint is null (known device)")
        void nao_dispara_sem_fingerprint() {
            assertThat(rule.evaluate(buildRequest(100_000L, null), baseContext())).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when amount is exactly at threshold (not over)")
        void nao_dispara_quando_valor_igual_ao_limite() {
            assertThat(rule.evaluate(buildRequest(50_000L, "fp-abc"), baseContext())).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 15 when fingerprint present and amount over 50 000 cents")
        void dispara_com_fingerprint_e_valor_alto() {
            assertThat(rule.evaluate(buildRequest(50_001L, "fp-abc"), baseContext())).isEqualTo(15);
        }

        @Test
        @DisplayName("returns 0 when fingerprint present but amount low")
        void nao_dispara_com_fingerprint_e_valor_baixo() {
            assertThat(rule.evaluate(buildRequest(1_000L, "fp-abc"), baseContext())).isEqualTo(0);
        }

        @Test
        @DisplayName("rule ID is NEW_DEVICE_HIGH_VALUE")
        void rule_id_correto() {
            assertThat(rule.getRuleId()).isEqualTo("NEW_DEVICE_HIGH_VALUE");
        }
    }

    // -------------------------------------------------------------------------
    // UnusualHourRule — tested with a fixed context; time-dependent logic is
    // tested indirectly (we verify the rule evaluates without errors)
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("UnusualHourRule")
    class UnusualHourRuleTests {

        private final UnusualHourRule rule = new UnusualHourRule();

        @Test
        @DisplayName("rule returns 0 or 10 — never negative, never > 10")
        void retorna_zero_ou_dez_nunca_negativo() {
            int points = rule.evaluate(buildRequest(50_000L, null), baseContext());
            assertThat(points).isIn(0, 10);
        }

        @Test
        @DisplayName("returns 0 when amount is below threshold regardless of hour")
        void nao_dispara_para_valor_baixo() {
            int points = rule.evaluate(buildRequest(1_000L, null), baseContext());
            assertThat(points).isEqualTo(0);
        }

        @Test
        @DisplayName("rule ID is UNUSUAL_HOUR")
        void rule_id_correto() {
            assertThat(rule.getRuleId()).isEqualTo("UNUSUAL_HOUR");
        }
    }

    // -------------------------------------------------------------------------
    // FirstPurchaseMaxValueRule
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("FirstPurchaseMaxValueRule")
    class FirstPurchaseMaxValueRuleTests {

        private final FirstPurchaseMaxValueRule rule = new FirstPurchaseMaxValueRule();

        @Test
        @DisplayName("returns 0 when not first purchase")
        void nao_dispara_quando_nao_e_primeira_compra() {
            FraudRuleContext ctx = new FraudRuleContext(0L, 0L, false, 0L, false);
            assertThat(rule.evaluate(buildRequest(99_999L, null), ctx)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when first purchase but amount below threshold")
        void nao_dispara_quando_primeira_compra_valor_baixo() {
            FraudRuleContext ctx = new FraudRuleContext(0L, 0L, false, 0L, true);
            assertThat(rule.evaluate(buildRequest(99_998L, null), ctx)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 20 when first purchase and amount exactly at threshold")
        void dispara_quando_primeira_compra_valor_igual_ao_limite() {
            FraudRuleContext ctx = new FraudRuleContext(0L, 0L, false, 0L, true);
            assertThat(rule.evaluate(buildRequest(99_999L, null), ctx)).isEqualTo(20);
        }

        @Test
        @DisplayName("returns 20 when first purchase and amount above threshold")
        void dispara_quando_primeira_compra_valor_acima_do_limite() {
            FraudRuleContext ctx = new FraudRuleContext(0L, 0L, false, 0L, true);
            assertThat(rule.evaluate(buildRequest(200_000L, null), ctx)).isEqualTo(20);
        }

        @Test
        @DisplayName("rule ID is FIRST_PURCHASE_MAX_VALUE")
        void rule_id_correto() {
            assertThat(rule.getRuleId()).isEqualTo("FIRST_PURCHASE_MAX_VALUE");
        }
    }
}
