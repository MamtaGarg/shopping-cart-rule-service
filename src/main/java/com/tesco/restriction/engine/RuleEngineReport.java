package com.tesco.restriction.engine;

import com.tesco.restriction.model.RestrictionStatus;
import com.tesco.restriction.model.RuleViolation;
import com.tesco.restriction.rule.RuleResult;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregated result returned by the {@link RuleEngine} after evaluating all registered
 * rules against a shopping basket.
 *
 * <p>The {@link #overallStatus()} is {@link RestrictionStatus#BREACHED} if <em>any</em>
 * rule is breached, and {@link RestrictionStatus#MET} only when every rule is satisfied.
 *
 * @param orderId       the basket / order identifier evaluated
 * @param ruleResults   individual result per rule (in evaluation order)
 * @param evaluatedAt   timestamp when the engine completed the evaluation
 */
public record RuleEngineReport(
        String          orderId,
        List<RuleResult> ruleResults,
        Instant          evaluatedAt
) {

    /** Compact constructor — makes the results list immutable. */
    public RuleEngineReport {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be null or blank");
        }
        if (ruleResults == null) {
            throw new IllegalArgumentException("ruleResults must not be null");
        }
        ruleResults = List.copyOf(ruleResults);
        evaluatedAt = (evaluatedAt != null) ? evaluatedAt : Instant.now();
    }

    /** Convenience constructor that stamps the current time. */
    public RuleEngineReport(String orderId, List<RuleResult> ruleResults) {
        this(orderId, ruleResults, Instant.now());
    }

    // -------------------------------------------------------------------------
    // Aggregation helpers
    // -------------------------------------------------------------------------

    /**
     * Overall pass/fail for the entire basket.
     * Returns {@link RestrictionStatus#BREACHED} if at least one rule is violated.
     */
    public RestrictionStatus overallStatus() {
        return ruleResults.stream()
                .anyMatch(RuleResult::isViolated)
                ? RestrictionStatus.BREACHED
                : RestrictionStatus.MET;
    }

    /**
     * Returns only the violated rule results.
     */
    public List<RuleResult> breachedRules() {
        return ruleResults.stream()
                .filter(RuleResult::isViolated)
                .toList();
    }

    /**
     * Returns a flat list of every individual {@link RuleViolation} across all rules.
     */
    public List<RuleViolation> allViolations() {
        return ruleResults.stream()
                .flatMap(r -> r.violations().stream())
                .toList();
    }

    /**
     * Returns {@code true} if the basket is fully compliant with all rules.
     */
    public boolean isCompliant() {
        return overallStatus().isMet();
    }

    // -------------------------------------------------------------------------
    // Human-readable summary
    // -------------------------------------------------------------------------

    /**
     * Produces a multi-line plain-text summary suitable for logging or display.
     */
    public String summary() {
        var sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("  RESTRICTION RULE ENGINE REPORT\n");
        sb.append("========================================\n");
        sb.append("  Order ID       : ").append(orderId).append('\n');
        sb.append("  Evaluated At   : ").append(evaluatedAt).append('\n');
        sb.append("  Overall Status : ").append(overallStatus()).append('\n');
        sb.append("  Rules Evaluated: ").append(ruleResults.size()).append('\n');
        sb.append("----------------------------------------\n");

        ruleResults.forEach(result -> {
            sb.append("  ► Rule: ").append(result.ruleName())
              .append("  [").append(result.status()).append("]\n");

            // Use sealed-interface pattern matching (Java 21)
            switch (result) {
                case RuleResult.Compliant ignored ->
                        sb.append("    ✓ No violations found.\n");
                case RuleResult.Violated violated -> {
                    violated.violations().forEach(v ->
                            sb.append("    ✗ ").append(v.message()).append('\n'));
                }
            }
        });

        sb.append("========================================\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "RuleEngineReport{orderId='%s', overallStatus=%s, rules=%d, violations=%d}"
                .formatted(orderId, overallStatus(), ruleResults.size(), allViolations().size());
    }
}
