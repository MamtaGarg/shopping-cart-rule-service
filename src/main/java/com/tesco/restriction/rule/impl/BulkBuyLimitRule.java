package com.tesco.restriction.rule.impl;

import com.tesco.restriction.model.RuleViolation;
import com.tesco.restriction.rule.RestrictionRule;
import com.tesco.restriction.rule.RuleContext;
import com.tesco.restriction.rule.RuleResult;

import java.util.List;

/**
 * <b>BulkBuyLimit</b> – enforces a maximum quantity cap on any single product in the basket.
 *
 * <p>A customer may not place more than {@code maxQuantityPerProduct} units of the same
 * product in one order, regardless of category.  This prevents stockpiling behaviour
 * and satisfies Tesco's bulk-purchase policy.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Iterate every line item in the cart.</li>
 *   <li>If a product's quantity exceeds {@code maxQuantityPerProduct}, record a violation.</li>
 *   <li>Collect all violations — the rule does <em>not</em> short-circuit, so customers
 *       see every offending product in one response rather than one at a time.</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>
 *   Max = 10
 *   productId=3, qty=8  → OK (8 ≤ 10)
 *   productId=5, qty=12 → BREACHED (12 > 10)
 * </pre>
 */
public final class BulkBuyLimitRule implements RestrictionRule {

    public static final String RULE_NAME = "BulkBuyLimit";

    /** Default cap applied if no custom value is provided. */
    public static final int DEFAULT_MAX_QUANTITY = 10;

    private final int maxQuantityPerProduct;

    /**
     * Creates a rule with the default limit ({@value DEFAULT_MAX_QUANTITY}).
     */
    public BulkBuyLimitRule() {
        this(DEFAULT_MAX_QUANTITY);
    }

    /**
     * Creates a rule with a custom per-product quantity limit.
     *
     * @param maxQuantityPerProduct maximum allowed quantity for a single product (must be ≥ 1)
     */
    public BulkBuyLimitRule(int maxQuantityPerProduct) {
        if (maxQuantityPerProduct < 1) {
            throw new IllegalArgumentException(
                    "maxQuantityPerProduct must be at least 1, got: " + maxQuantityPerProduct);
        }
        this.maxQuantityPerProduct = maxQuantityPerProduct;
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public String getDescription() {
        return "Cannot buy more than %d units of any single product".formatted(maxQuantityPerProduct);
    }

    @Override
    public int priority() {
        return 10; // evaluated first — broad catch-all before category-specific rules
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        List<RuleViolation> violations = context.cart().items().stream()
                .filter(item -> item.quantity() > maxQuantityPerProduct)
                .map(item -> RuleViolation.forProduct(item.productId(), item.quantity(), maxQuantityPerProduct))
                .toList();

        return violations.isEmpty()
                ? RuleResult.compliant(RULE_NAME)
                : RuleResult.violated(RULE_NAME, violations);
    }

    public int getMaxQuantityPerProduct() {
        return maxQuantityPerProduct;
    }

    @Override
    public String toString() {
        return "BulkBuyLimitRule{maxQuantityPerProduct=%d}".formatted(maxQuantityPerProduct);
    }
}
