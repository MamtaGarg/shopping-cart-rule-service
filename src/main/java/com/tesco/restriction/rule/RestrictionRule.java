package com.tesco.restriction.rule;

/**
 * Contract for all shopping-cart restriction rules.
 *
 * <p><b>Extension point:</b> implement this interface to add a new rule — no changes to
 * the engine or existing rules are required (Open/Closed Principle). Implementations
 * must be stateless and thread-safe so the engine can reuse a single instance across
 * concurrent order evaluations.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>Rules receive a {@link RuleContext} rather than a raw {@link com.tesco.restriction.model.ShoppingCart}
 *       so that future metadata (customer tier, store type, feature flags, etc.) can be
 *       threaded through without changing the interface.</li>
 *   <li>The return type is the sealed {@link RuleResult}, enabling exhaustive
 *       switch-expression pattern-matching at the call site.</li>
 *   <li>Rules are identified by a stable {@link #getName()} key that matches the
 *       business name (e.g. {@code "BulkBuyLimit"}) so reports are human-readable and
 *       can be correlated with compliance documents.</li>
 * </ul>
 */
public interface RestrictionRule {

    /**
     * Stable, human-readable rule identifier (e.g. {@code "BulkBuyLimit"}).
     * Used in reports, logs, and rule-engine configuration.
     */
    String getName();

    /**
     * Short description of what this rule enforces.
     * Included in violation messages and audit reports.
     */
    String getDescription();

    /**
     * Evaluates the rule against the supplied context.
     *
     * @param context contains the shopping cart and any runtime metadata
     * @return a {@link RuleResult.Compliant} if the cart satisfies the rule, or a
     *         {@link RuleResult.Violated} with full violation detail otherwise
     */
    RuleResult evaluate(RuleContext context);

    /**
     * Optional priority hint — lower values are evaluated first.
     * The engine uses this to order rules for deterministic, fast-fail behaviour.
     * Defaults to {@code 100}; override to adjust evaluation order.
     */
    default int priority() {
        return 100;
    }
}
