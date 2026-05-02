package com.tesco.restriction.engine;

import com.tesco.restriction.model.CartItem;
import com.tesco.restriction.model.RestrictionStatus;
import com.tesco.restriction.model.ShoppingCart;
import com.tesco.restriction.rule.RestrictionRule;
import com.tesco.restriction.rule.RuleContext;
import com.tesco.restriction.rule.RuleResult;
import com.tesco.restriction.rule.impl.BulkBuyLimitCategoryRule;
import com.tesco.restriction.rule.impl.BulkBuyLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RuleEngine")
class RuleEngineTest {

    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = RuleEngine.builder()
                .withRule(new BulkBuyLimitRule(10))
                .withRule(new BulkBuyLimitCategoryRule("Paracetamol", 5))
                .build();
    }

    // -------------------------------------------------------------------------
    // Full Tesco requirement scenario
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MET — canonical Tesco requirements example returns overall status MET")
    void tescoRequirementsScenario() {
        ShoppingCart cart = new ShoppingCart("ORDER-001", List.of(
                new CartItem("1", "Paracetamol", 3),
                new CartItem("2", "analgesic",   3),
                new CartItem("3", "chocolate",   8),
                new CartItem("4", "Paracetamol", 2)
        ));

        RuleEngineReport report = engine.evaluate(cart);

        assertThat(report.overallStatus()).isEqualTo(RestrictionStatus.MET);
        assertThat(report.isCompliant()).isTrue();
        assertThat(report.breachedRules()).isEmpty();
        assertThat(report.allViolations()).isEmpty();
        assertThat(report.ruleResults()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // BREACHED scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("when rules are breached")
    class WhenBreached {

        @Test
        @DisplayName("reports BREACHED when BulkBuyLimit is violated")
        void bulkBuyLimitBreached() {
            ShoppingCart cart = new ShoppingCart("ORDER-002", List.of(
                    new CartItem("1", "soda", 11) // 11 > 10
            ));

            RuleEngineReport report = engine.evaluate(cart);

            assertThat(report.overallStatus()).isEqualTo(RestrictionStatus.BREACHED);
            assertThat(report.breachedRules()).hasSize(1);
            assertThat(report.breachedRules().get(0).ruleName()).isEqualTo("BulkBuyLimit");
        }

        @Test
        @DisplayName("reports BREACHED when BulkBuyLimitCategory is violated")
        void categoryLimitBreached() {
            ShoppingCart cart = new ShoppingCart("ORDER-003", List.of(
                    new CartItem("1", "Paracetamol", 4),
                    new CartItem("2", "Paracetamol", 3)  // total 7 > 5
            ));

            RuleEngineReport report = engine.evaluate(cart);

            assertThat(report.overallStatus()).isEqualTo(RestrictionStatus.BREACHED);
            assertThat(report.breachedRules()).extracting(RuleResult::ruleName)
                    .containsExactly("BulkBuyLimitCategory");
        }

        @Test
        @DisplayName("reports BREACHED with violations from both rules when both are violated")
        void bothRulesBreached() {
            ShoppingCart cart = new ShoppingCart("ORDER-004", List.of(
                    new CartItem("1", "Paracetamol", 6),  // category total 6 > 5
                    new CartItem("2", "soda",        11)  // product qty 11 > 10
            ));

            RuleEngineReport report = engine.evaluate(cart);

            assertThat(report.overallStatus()).isEqualTo(RestrictionStatus.BREACHED);
            assertThat(report.breachedRules()).hasSize(2);
            assertThat(report.allViolations()).hasSize(2);
        }

        @Test
        @DisplayName("evaluates all rules — does not short-circuit on first breach")
        void allRulesEvaluatedOnBreach() {
            ShoppingCart cart = new ShoppingCart("ORDER-005", List.of(
                    new CartItem("1", "Paracetamol", 9),  // product qty 9 ≤ 10 (BulkBuyLimit MET)
                    new CartItem("2", "Paracetamol", 1)   // category total 10 > 5 (category BREACHED)
                                                          // but both rules should still run
            ));

            RuleEngineReport report = engine.evaluate(cart);

            // Both rules were evaluated
            assertThat(report.ruleResults()).hasSize(2);
            // One MET, one BREACHED
            assertThat(report.ruleResults())
                    .extracting(RuleResult::status)
                    .containsExactlyInAnyOrder(RestrictionStatus.MET, RestrictionStatus.BREACHED);
        }
    }

    // -------------------------------------------------------------------------
    // Report contents
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("report metadata")
    class ReportMetadata {

        @Test
        @DisplayName("report contains orderId from the cart")
        void containsOrderId() {
            ShoppingCart cart = new ShoppingCart("MY-ORDER-XYZ", List.of(
                    new CartItem("1", "juice", 1)
            ));
            RuleEngineReport report = engine.evaluate(cart);
            assertThat(report.orderId()).isEqualTo("MY-ORDER-XYZ");
        }

        @Test
        @DisplayName("report has a non-null evaluatedAt timestamp")
        void hasTimestamp() {
            ShoppingCart cart = new ShoppingCart("ORDER-T", List.of(
                    new CartItem("1", "juice", 1)
            ));
            assertThat(engine.evaluate(cart).evaluatedAt()).isNotNull();
        }

        @Test
        @DisplayName("summary string includes orderId and overall status")
        void summaryContainsKeyInfo() {
            ShoppingCart cart = new ShoppingCart("ORDER-S", List.of(
                    new CartItem("1", "juice", 1)
            ));
            String summary = engine.evaluate(cart).summary();
            assertThat(summary).contains("ORDER-S", "MET");
        }
    }

    // -------------------------------------------------------------------------
    // Builder validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("throws when no rules are registered")
        void noRulesThrows() {
            assertThatThrownBy(() -> RuleEngine.builder().build())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("rules are sorted by priority ascending")
        void rulesSortedByPriority() {
            // BulkBuyLimitRule has priority 10, BulkBuyLimitCategoryRule has priority 20
            RuleEngine e = RuleEngine.builder()
                    .withRule(new BulkBuyLimitCategoryRule("test", 5))  // priority 20 added first
                    .withRule(new BulkBuyLimitRule(10))                  // priority 10 added second
                    .build();

            assertThat(e.getRules().get(0)).isInstanceOf(BulkBuyLimitRule.class);
            assertThat(e.getRules().get(1)).isInstanceOf(BulkBuyLimitCategoryRule.class);
        }

        @Test
        @DisplayName("withRules accepts a list of rules")
        void withRulesAcceptsList() {
            List<RestrictionRule> ruleList = List.of(
                    new BulkBuyLimitRule(),
                    new BulkBuyLimitCategoryRule("analgesic", 3)
            );

            RuleEngine e = RuleEngine.builder().withRules(ruleList).build();
            assertThat(e.getRules()).hasSize(2);
        }

        @Test
        @DisplayName("throws NullPointerException when null rule is passed")
        void nullRuleThrows() {
            assertThatThrownBy(() -> RuleEngine.builder().withRule(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -------------------------------------------------------------------------
    // Custom rule extension test (demonstrates extensibility)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("accepts a custom anonymous rule — demonstrates extension point")
    void customRuleExtensionPoint() {
        // Inline custom rule: no alcohol allowed at all
        RestrictionRule noAlcoholRule = new RestrictionRule() {
            @Override public String getName() { return "NoAlcohol"; }
            @Override public String getDescription() { return "Alcohol is not sold online"; }
            @Override
            public RuleResult evaluate(RuleContext ctx) {
                boolean hasAlcohol = ctx.cart().items().stream()
                        .anyMatch(i -> i.categoryLower().contains("alcohol"));
                return hasAlcohol
                        ? RuleResult.violated(getName(), com.tesco.restriction.model.RuleViolation
                                .forCategory("alcohol", 1, 0))
                        : RuleResult.compliant(getName());
            }
        };

        RuleEngine customEngine = RuleEngine.builder().withRule(noAlcoholRule).build();

        ShoppingCart cartWithAlcohol = new ShoppingCart("ORDER-BOOZE", List.of(
                new CartItem("99", "alcohol", 1)
        ));

        assertThat(customEngine.evaluate(cartWithAlcohol).overallStatus())
                .isEqualTo(RestrictionStatus.BREACHED);
    }
}
