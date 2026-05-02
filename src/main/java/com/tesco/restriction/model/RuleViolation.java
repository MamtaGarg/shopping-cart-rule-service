package com.tesco.restriction.model;

/**
 * Describes a single violation detected during a rule evaluation.
 *
 * <p>A single rule may produce multiple violations (e.g. several products that
 * individually exceed the bulk-buy limit), so {@link com.tesco.restriction.rule.RuleResult}
 * holds a list of these.
 *
 * @param subject      human-readable identifier of what was checked
 *                     (e.g. "productId=3" or "category=Paracetamol")
 * @param actualValue  the value that triggered the violation (e.g. 12)
 * @param limitValue   the configured limit that was exceeded (e.g. 10)
 * @param message      free-text description of the violation
 */
public record RuleViolation(
        String subject,
        int    actualValue,
        int    limitValue,
        String message
) {

    /** Compact constructor — ensures message is never null/blank. */
    public RuleViolation {
        if (message == null || message.isBlank()) {
            message = "Value %d exceeds limit %d for %s".formatted(actualValue, limitValue, subject);
        }
    }

    /**
     * Convenience factory for a product-level violation.
     */
    public static RuleViolation forProduct(String productId, int actualQty, int limitQty) {
        return new RuleViolation(
                "productId=" + productId,
                actualQty,
                limitQty,
                "Product '%s' has quantity %d which exceeds the per-product limit of %d"
                        .formatted(productId, actualQty, limitQty)
        );
    }

    /**
     * Convenience factory for a category-level violation.
     */
    public static RuleViolation forCategory(String category, int actualQty, int limitQty) {
        return new RuleViolation(
                "category=" + category,
                actualQty,
                limitQty,
                "Category '%s' has total quantity %d which exceeds the category limit of %d"
                        .formatted(category, actualQty, limitQty)
        );
    }

    @Override
    public String toString() {
        return "RuleViolation{subject='%s', actual=%d, limit=%d, message='%s'}"
                .formatted(subject, actualValue, limitValue, message);
    }
}
