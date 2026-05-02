package com.tesco.restriction.rule.impl;

import com.tesco.restriction.model.RuleViolation;
import com.tesco.restriction.rule.RestrictionRule;
import com.tesco.restriction.rule.RuleContext;
import com.tesco.restriction.rule.RuleResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <b>BulkBuyLimitCategory</b> – enforces a maximum <em>aggregate</em> quantity cap for
 * one or more restricted product categories.
 *
 * <p>All items belonging to a restricted category are summed across the basket.  If the
 * total for any restricted category exceeds its configured limit, the rule is BREACHED.
 * This handles scenarios such as the Paracetamol safe-supply regulation where a customer
 * must not be sold more than N units of the drug — even if they spread the quantity
 * across multiple product lines (e.g. productId=1 + productId=4 both in "Paracetamol").
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Build a category → total-quantity map from the cart.</li>
 *   <li>For each restricted category, look up the aggregate quantity.</li>
 *   <li>If the aggregate exceeds the per-category limit, record a violation.</li>
 * </ol>
 *
 * <h2>Example (Tesco scenario)</h2>
 * <pre>
 *   Restricted categories: {Paracetamol → max 5}
 *   Cart:
 *     productId=1, category=Paracetamol, qty=3
 *     productId=4, category=Paracetamol, qty=2
 *   Aggregate Paracetamol = 5  → MET (5 ≤ 5)
 * </pre>
 *
 * <h2>Extension: multiple categories</h2>
 * Pass multiple entries in the {@code categoryLimits} map to restrict several
 * categories in one rule instance — or register separate instances per category,
 * whichever suits the reporting granularity required.
 */
public final class BulkBuyLimitCategoryRule implements RestrictionRule {

    public static final String RULE_NAME = "BulkBuyLimitCategory";

    /**
     * Mapping of restricted category name (lower-cased) → maximum allowed aggregate quantity.
     * Stored lower-cased to guarantee case-insensitive matching at evaluation time.
     */
    private final Map<String, Integer> categoryLimits;

    /**
     * Creates a rule that restricts a single category.
     *
     * @param category     the restricted category name (case-insensitive)
     * @param maxQuantity  maximum aggregate quantity allowed across all items in that category
     */
    public BulkBuyLimitCategoryRule(String category, int maxQuantity) {
        this(Map.of(normalise(category), validated(maxQuantity, category)));
    }

    /**
     * Creates a rule that restricts multiple categories at once.
     *
     * <p>Keys are normalised to lower-case; values must be ≥ 1.
     *
     * @param categoryLimits map of category name → max aggregate quantity
     */
    public BulkBuyLimitCategoryRule(Map<String, Integer> categoryLimits) {
        if (categoryLimits == null || categoryLimits.isEmpty()) {
            throw new IllegalArgumentException("categoryLimits must contain at least one entry");
        }
        // Normalise all keys to lower-case and validate limits
        this.categoryLimits = categoryLimits.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        e -> normalise(e.getKey()),
                        e -> validated(e.getValue(), e.getKey())
                ));
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public String getDescription() {
        String limits = categoryLimits.entrySet().stream()
                .map(e -> "'%s' ≤ %d".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
        return "Cannot buy more than the allowed aggregate quantity per restricted category: [%s]".formatted(limits);
    }

    @Override
    public int priority() {
        return 20; // after BulkBuyLimitRule
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        // Compute aggregate quantities once, keyed by lower-cased category
        Map<String, Integer> aggregates = context.cart().quantityByCategory();

        List<RuleViolation> violations = categoryLimits.entrySet().stream()
                .filter(entry -> {
                    int actual = aggregates.getOrDefault(entry.getKey(), 0);
                    return actual > entry.getValue();
                })
                .map(entry -> {
                    int actual = aggregates.getOrDefault(entry.getKey(), 0);
                    return RuleViolation.forCategory(entry.getKey(), actual, entry.getValue());
                })
                .toList();

        return violations.isEmpty()
                ? RuleResult.compliant(RULE_NAME)
                : RuleResult.violated(RULE_NAME, violations);
    }

    /**
     * Returns an unmodifiable view of the category → limit configuration.
     * Useful for reporting and diagnostics.
     */
    public Map<String, Integer> getCategoryLimits() {
        return categoryLimits;
    }

    /**
     * Returns the set of restricted categories (lower-cased).
     */
    public Set<String> getRestrictedCategories() {
        return categoryLimits.keySet();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String normalise(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category name must not be null or blank");
        }
        return category.trim().toLowerCase();
    }

    private static int validated(int limit, String categoryForError) {
        if (limit < 1) {
            throw new IllegalArgumentException(
                    "Limit for category '%s' must be ≥ 1, got: %d".formatted(categoryForError, limit));
        }
        return limit;
    }

    @Override
    public String toString() {
        return "BulkBuyLimitCategoryRule{categoryLimits=%s}".formatted(categoryLimits);
    }
}
