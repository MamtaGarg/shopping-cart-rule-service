package com.tesco.restriction.rule.impl;

import com.tesco.restriction.model.CartItem;
import com.tesco.restriction.model.RestrictionStatus;
import com.tesco.restriction.model.ShoppingCart;
import com.tesco.restriction.rule.RuleContext;
import com.tesco.restriction.rule.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BulkBuyLimitRule")
class BulkBuyLimitRuleTest {

    private BulkBuyLimitRule rule;

    @BeforeEach
    void setUp() {
        rule = new BulkBuyLimitRule(10); // default: max 10 per product
    }

    // -------------------------------------------------------------------------
    // MET scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("when all products are within the limit")
    class WhenCompliant {

        @Test
        @DisplayName("returns MET for the canonical Tesco requirements example")
        void tescoRequirementsExample() {
            ShoppingCart cart = new ShoppingCart("ORDER-001", List.of(
                    new CartItem("1", "Paracetamol", 3),
                    new CartItem("2", "analgesic",   3),
                    new CartItem("3", "chocolate",   8),
                    new CartItem("4", "Paracetamol", 2)
            ));

            RuleResult result = rule.evaluate(new RuleContext(cart));

            assertThat(result.status()).isEqualTo(RestrictionStatus.MET);
            assertThat(result.violations()).isEmpty();
            assertThat(result).isInstanceOf(RuleResult.Compliant.class);
        }

        @Test
        @DisplayName("returns MET when a single product is exactly at the limit")
        void exactlyAtLimit() {
            ShoppingCart cart = new ShoppingCart("ORDER-002", List.of(
                    new CartItem("1", "soda", 10)
            ));

            RuleResult result = rule.evaluate(new RuleContext(cart));

            assertThat(result.status()).isEqualTo(RestrictionStatus.MET);
        }

        @ParameterizedTest(name = "qty={0} is within limit of 10")
        @ValueSource(ints = {1, 5, 9, 10})
        @DisplayName("returns MET for quantities from 1 to 10 inclusive")
        void withinAllowedRange(int qty) {
            ShoppingCart cart = new ShoppingCart("ORDER-003", List.of(
                    new CartItem("P1", "chips", qty)
            ));

            assertThat(rule.evaluate(new RuleContext(cart)).status())
                    .isEqualTo(RestrictionStatus.MET);
        }
    }

    // -------------------------------------------------------------------------
    // BREACHED scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("when any product exceeds the limit")
    class WhenBreached {

        @Test
        @DisplayName("returns BREACHED when a single product exceeds the limit")
        void singleProductExceedsLimit() {
            ShoppingCart cart = new ShoppingCart("ORDER-004", List.of(
                    new CartItem("1", "soda", 11)
            ));

            RuleResult result = rule.evaluate(new RuleContext(cart));

            assertThat(result.status()).isEqualTo(RestrictionStatus.BREACHED);
            assertThat(result.violations()).hasSize(1);
            assertThat(result.violations().get(0).subject()).isEqualTo("productId=1");
            assertThat(result.violations().get(0).actualValue()).isEqualTo(11);
            assertThat(result.violations().get(0).limitValue()).isEqualTo(10);
        }

        @Test
        @DisplayName("collects all violations when multiple products exceed the limit")
        void multipleProductsExceedLimit() {
            ShoppingCart cart = new ShoppingCart("ORDER-005", List.of(
                    new CartItem("1", "soda",   15),
                    new CartItem("2", "juice",   2),   // OK
                    new CartItem("3", "water",  12)    // also breached
            ));

            RuleResult result = rule.evaluate(new RuleContext(cart));

            assertThat(result.status()).isEqualTo(RestrictionStatus.BREACHED);
            assertThat(result.violations()).hasSize(2);
        }

        @ParameterizedTest(name = "qty={0} exceeds limit of 10")
        @ValueSource(ints = {11, 50, 100, 999})
        @DisplayName("returns BREACHED for quantities above 10")
        void aboveLimit(int qty) {
            ShoppingCart cart = new ShoppingCart("ORDER-006", List.of(
                    new CartItem("P1", "chips", qty)
            ));

            assertThat(rule.evaluate(new RuleContext(cart)).status())
                    .isEqualTo(RestrictionStatus.BREACHED);
        }
    }

    // -------------------------------------------------------------------------
    // Custom limit configuration
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("with custom max quantity")
    class CustomLimit {

        @Test
        @DisplayName("respects a custom limit of 3")
        void customLimit3() {
            BulkBuyLimitRule strictRule = new BulkBuyLimitRule(3);
            ShoppingCart cart = new ShoppingCart("ORDER-007", List.of(
                    new CartItem("A", "beer", 4) // 4 > 3 → BREACHED
            ));

            assertThat(strictRule.evaluate(new RuleContext(cart)).status())
                    .isEqualTo(RestrictionStatus.BREACHED);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for limit < 1")
        void invalidLimit() {
            assertThatThrownBy(() -> new BulkBuyLimitRule(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -------------------------------------------------------------------------
    // Metadata checks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("has correct rule name")
    void ruleName() {
        assertThat(rule.getName()).isEqualTo("BulkBuyLimit");
    }

    @Test
    @DisplayName("priority is lower than category rule (evaluated first)")
    void priority() {
        BulkBuyLimitCategoryRule categoryRule = new BulkBuyLimitCategoryRule("test", 5);
        assertThat(rule.priority()).isLessThan(categoryRule.priority());
    }
}
