package com.tesco.restriction.engine;

import com.tesco.restriction.model.ShoppingCart;
import com.tesco.restriction.rule.RestrictionRule;
import com.tesco.restriction.rule.RuleContext;
import com.tesco.restriction.rule.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the evaluation of all registered {@link RestrictionRule}s against a
 * shopping basket and produces a single {@link RuleEngineReport}.
 *
 * <h2>Thread Safety</h2>
 * {@code RuleEngine} is immutable after construction — rules are fixed at build time and
 * stored in an unmodifiable list.  It is safe to share a single engine instance across
 * multiple concurrent order-processing threads.
 *
 * <h2>Evaluation Strategy</h2>
 * <ul>
 *   <li>Rules are sorted by {@link RestrictionRule#priority()} (ascending) before evaluation.</li>
 *   <li>All rules are evaluated regardless of earlier results (non-short-circuit), so the
 *       customer sees <em>every</em> violated rule in one response.</li>
 *   <li>Future option: inject a {@code EvaluationStrategy} to switch between fail-fast and
 *       full-scan modes.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RuleEngine engine = RuleEngine.builder()
 *     .withRule(new BulkBuyLimitRule(10))
 *     .withRule(new BulkBuyLimitCategoryRule("Paracetamol", 5))
 *     .build();
 *
 * RuleEngineReport report = engine.evaluate(cart);
 * System.out.println(report.overallStatus()); // MET or BREACHED
 * }</pre>
 */
public final class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    /** Rules sorted by priority at build time — immutable after construction. */
    private final List<RestrictionRule> rules;

    private RuleEngine(List<RestrictionRule> rules) {
        // Sort once at construction; priority() is stable so no need to re-sort per request
        List<RestrictionRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(RestrictionRule::priority));
        this.rules = Collections.unmodifiableList(sorted);
    }

    // -------------------------------------------------------------------------
    // Core evaluation
    // -------------------------------------------------------------------------

    /**
     * Evaluates all registered rules against the given cart.
     *
     * @param cart the basket to check; must not be null
     * @return a {@link RuleEngineReport} capturing per-rule results and overall status
     */
    public RuleEngineReport evaluate(ShoppingCart cart) {
        if (cart == null) {
            throw new IllegalArgumentException("ShoppingCart must not be null");
        }

        log.info("Evaluating {} rule(s) for orderId='{}'", rules.size(), cart.orderId());

        RuleContext context = new RuleContext(cart);

        List<RuleResult> results = rules.stream()
                .map(rule -> {
                    log.debug("Evaluating rule '{}' for orderId='{}'", rule.getName(), cart.orderId());
                    RuleResult result = rule.evaluate(context);
                    log.debug("Rule '{}' → {} for orderId='{}'",
                            rule.getName(), result.status(), cart.orderId());
                    return result;
                })
                .toList();

        RuleEngineReport report = new RuleEngineReport(cart.orderId(), results);
        log.info("Evaluation complete for orderId='{}' → overallStatus={}",
                cart.orderId(), report.overallStatus());

        return report;
    }

    /**
     * Returns the list of registered rules in evaluation order (sorted by priority).
     */
    public List<RestrictionRule> getRules() {
        return rules;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Returns a new {@link Builder} for constructing a {@code RuleEngine}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link RuleEngine}.
     *
     * <p>Supports adding rules individually ({@link #withRule}) or in bulk
     * ({@link #withRules}).  The engine is finalised via {@link #build()}.
     */
    public static final class Builder {

        private final List<RestrictionRule> rules = new ArrayList<>();

        private Builder() {}

        /**
         * Adds a single rule.
         *
         * @param rule the restriction rule to register; must not be null
         * @return this builder (fluent)
         */
        public Builder withRule(RestrictionRule rule) {
            if (rule == null) {
                throw new IllegalArgumentException("Rule must not be null");
            }
            rules.add(rule);
            return this;
        }

        /**
         * Adds multiple rules at once.
         *
         * @param ruleList collection of rules to register; must not be null
         * @return this builder (fluent)
         */
        public Builder withRules(List<RestrictionRule> ruleList) {
            if (ruleList == null) {
                throw new IllegalArgumentException("Rule list must not be null");
            }
            ruleList.forEach(this::withRule);
            return this;
        }

        /**
         * Builds an immutable {@link RuleEngine} with the registered rules.
         *
         * @throws IllegalStateException if no rules have been registered
         */
        public RuleEngine build() {
            if (rules.isEmpty()) {
                throw new IllegalStateException(
                        "RuleEngine requires at least one rule — use withRule() before build()");
            }
            return new RuleEngine(rules);
        }
    }
}
