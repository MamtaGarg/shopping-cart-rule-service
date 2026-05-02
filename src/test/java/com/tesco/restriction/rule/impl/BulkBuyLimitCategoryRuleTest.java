package com.tesco.restriction.rule.impl;

import com.tesco.restriction.model.CartItem;
import com.tesco.restriction.model.RestrictionStatus;
import com.tesco.restriction.model.ShoppingCart;
import com.tesco.restriction.rule.RuleContext;
import com.tesco.restriction.rule.RuleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BulkBuyLimitCategoryRule")
class BulkBuyLimitCategoryRuleTest {

    // -------------------------------------------------------------------------
    // MET scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("when category aggregate is within limit")
    class WhenCompliant {

        @Test
        @DisplayName("returns MET — canonical Tesco example: Paracetamol total exactly equals limit 5")
        void tescoRequirementsExample_exactlyAtLimit() {
            // productId=1, Paracetamol, qty=3  +  productId=4, Paracetamol, qty=2 → total=5
            BulkBuyLimitCategoryRule rule = new BulkBuyLimitCategoryRule("Paracetamol", 5);

            ShoppingCart cart = new ShoppingCart("ORDER-001", List.of(
                    new CartItem("1", "Paracetamol", 3),
                    new CartItem("2", "analgesic",   3),
                    new CartItem("3", "chocolate",   8),
                    new CartItem("4", "Paracetamol", 2)
            ));

            RuleResult result = rule.evaluate(new RuleContext(cart));

            assertThat(result.status()).isEqualTo(RestrictionStatus.MET);
            assertThat(result.violations()).isEmpty();
        }

        @Test
        @DisplayName("returns MET when restricted category is absent from the cart")
        void categoryNotInCart() {
            BulkBuyLimitCategoryRule rule = new BulkBuyLimitCategoryRule("Paracetamol", 5);

            ShoppingCart cart = new ShoppingCart("ORDER-002", List.of(
                    new CartItem("1", "chocolate", 9)
            ));

            assertThat(rule.evaluate(new RuleContext(cart)).status())
                    .isEqualTo(RestrictionStatus.MET);
        }

        @Test
        @DisplayName("category comparison is case-insensitive")
        void caseInsensitiveMatch() {
            BulkBuyLimitCategoryRule rule = new BulkBuyLimitCategoryRule("PARACETAMOL", 5);

            ShoppingCart cart = new ShoppingCart("ORDER-003", List.of(
                    new CartItem("1", "paracetamol", 3),
                    new CartItem("2", "Paracetamol", 2)  // total = 5
            ));

            assertThat(rule.evaluate(new RuleContext(cart)).status())
                    .isEqualTo(RestrictionStatus.MET);
        }
    }

    // -------------------------------------------------------------------------
    // BREACHED scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("when category aggregate exceeds limit")
    class WhenBreached {

        @Test
        @DisplayName("returns BREACHED when aggregate Paracetamol exceeds 5")
        void paracetamolAggregateExceedsLimit() {
            BulkBuyLimitCategoryRule rule = new BulkBuyLimitCategoryRule("Paracetamol", 5);

            ShoppingCart cart = new ShoppingCart("ORDER-004", List.of(
                    new CartItem("1", "Paracetamol", 4),
                    new CartItem("2", "Paracetamol", 3)   // total = 7 > 5
            ));

            RuleResult result = rule.evaluate(new RuleContext(cart));

            assertThat(result.status()).isEqualTo(RestrictionStatus.BREACHED);
            assertThat(result.violations()).hasSize(1);
            assertThat(result.violations().get(0).actualValue()).isEqualTo(7);
            assertThat(result.violations().get(0).limitValue()).isEqualTo(5);
        }

        @Test
        @DisplayName("violation subject correctly identifies the category")
        void violationSubjectIsCategory() {
            BulkBuyLimitCategoryRule rule = new BulkBuyLimitCategoryRule("Paracetamol", 5);
            ShoppingCart cart = new ShoppingCart("ORDER-005", List.of(
                    new CartItem("1", "Paracetamol", 6)
            ));

            RuleResult result = rule.evaluate(new RuleContext(cart));

            assertThat(result.violations().get(0).subject()).isEqualTo("category=paracetamol");
        }
    }

    // -------------------------------------------------------------------------
    // Multi-category scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("with multiple restricted categories")
    class MultiCategory {

        @Test
        @DisplayName("MET when both categories are within limits")
        void bothWithinLimits() {
            BulkBuyLimitCategoryRule rule = new BulkBuyLimitCategoryRule(Map.of(
                    "Paracetamol", 5,
                    "analgesic",   3
            ));

            ShoppingCart cart = new ShoppingCart("ORDER-006", List.of(
                    new CartItem("1", "Paracetamol", 2),
                    new CartItem("2", "analgesic",   3),
                    new CartItem("3", "chocolate",   9)
            ));

            assertThat(rule.evaluate(new RuleContext(cart)).status())
                    .isEqualTo(RestrictionStatus.MET);
        }

        @Test
        @DisplayName("BREACHED with two violations when both categories exceed their limits")
        void bothBreached() {
            BulkBuyLimitCategoryRule rule = new BulkBuyLimitCategoryRule(Map.of(
                    "Paracetamol", 5,
                    "analgesic",   3
            ));

            ShoppingCart cart = new ShoppingCart("ORDER-007", List.of(
                    new CartItem("1", "Paracetamol", 6),   // 6 > 5
                    new CartItem("2", "analgesic",   4)    // 4 > 3
            ));

            RuleResult result = rule.evaluate(new RuleContext(cart));

            assertThat(result.status()).isEqualTo(RestrictionStatus.BREACHED);
            assertThat(result.violations()).hasSize(2);
        }

        @Test
        @DisplayName("BREACHED with one violation when only one category exceeds its limit")
        void oneCategoryBreached() {
            BulkBuyLimitCategoryRule rule = new BulkBuyLimitCategoryRule(Map.of(
                    "Paracetamol", 5,
                    "analgesic",   3
            ));

            ShoppingCart cart = new ShoppingCart("ORDER-008", List.of(
                    new CartItem("1", "Paracetamol", 2),
                    new CartItem("2", "analgesic",   4)    // 4 > 3 → only this breaches
            ));

            RuleResult result = rule.evaluate(new RuleContext(cart));

            assertThat(result.status()).isEqualTo(RestrictionStatus.BREACHED);
            assertThat(result.violations()).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // Validation tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("throws for null category")
        void nullCategory() {
            assertThatThrownBy(() -> new BulkBuyLimitCategoryRule(null, 5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for blank category")
        void blankCategory() {
            assertThatThrownBy(() -> new BulkBuyLimitCategoryRule("  ", 5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for limit < 1")
        void zeroLimit() {
            assertThatThrownBy(() -> new BulkBuyLimitCategoryRule("Paracetamol", 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for empty category limits map")
        void emptyMap() {
            assertThatThrownBy(() -> new BulkBuyLimitCategoryRule(Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("has correct rule name")
    void ruleName() {
        BulkBuyLimitCategoryRule rule = new BulkBuyLimitCategoryRule("Paracetamol", 5);
        assertThat(rule.getName()).isEqualTo("BulkBuyLimitCategory");
    }
}
