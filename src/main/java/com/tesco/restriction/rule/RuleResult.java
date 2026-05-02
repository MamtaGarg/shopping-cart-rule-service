package com.tesco.restriction.rule;

import com.tesco.restriction.model.RestrictionStatus;
import com.tesco.restriction.model.RuleViolation;

import java.util.List;

/**
 * Sealed discriminated-union type representing the outcome of a single rule evaluation.
 *
 * <p>Two permitted sub-types:
 * <ul>
 *   <li>{@link Compliant}  – the cart satisfies the rule (status = MET).</li>
 *   <li>{@link Violated}   – the cart breaches the rule (status = BREACHED), carrying
 *                            a non-empty list of {@link RuleViolation} details.</li>
 * </ul>
 *
 * <p>Using a sealed interface with records enables exhaustive pattern-matching in switch
 * expressions, so any code consuming results gets a compile-time guarantee that it handles
 * every possible outcome (Java 21 feature).
 */
public sealed interface RuleResult permits RuleResult.Compliant, RuleResult.Violated {

    /** The name of the rule that produced this result. */
    String ruleName();

    /** Overall pass/fail status for this rule. */
    RestrictionStatus status();

    // -------------------------------------------------------------------------
    // Permitted sub-types
    // -------------------------------------------------------------------------

    /**
     * The cart is compliant with the rule — no violations found.
     *
     * @param ruleName name of the rule
     */
    record Compliant(String ruleName) implements RuleResult {

        @Override
        public RestrictionStatus status() {
            return RestrictionStatus.MET;
        }

        @Override
        public String toString() {
            return "RuleResult.Compliant{rule='%s', status=MET}".formatted(ruleName);
        }
    }

    /**
     * The cart violates the rule — one or more violations were detected.
     *
     * @param ruleName   name of the rule
     * @param violations non-empty list of specific violations
     */
    record Violated(String ruleName, List<RuleViolation> violations) implements RuleResult {

        /** Compact constructor — ensures violations list is non-empty and immutable. */
        public Violated {
            if (violations == null || violations.isEmpty()) {
                throw new IllegalArgumentException(
                        "A Violated result must contain at least one RuleViolation");
            }
            violations = List.copyOf(violations);
        }

        @Override
        public RestrictionStatus status() {
            return RestrictionStatus.BREACHED;
        }

        @Override
        public String toString() {
            return "RuleResult.Violated{rule='%s', status=BREACHED, violations=%s}"
                    .formatted(ruleName, violations);
        }
    }

    // -------------------------------------------------------------------------
    // Convenience factories
    // -------------------------------------------------------------------------

    /** Creates a {@link Compliant} result for the given rule name. */
    static RuleResult compliant(String ruleName) {
        return new Compliant(ruleName);
    }

    /** Creates a {@link Violated} result for the given rule name and violations. */
    static RuleResult violated(String ruleName, List<RuleViolation> violations) {
        return new Violated(ruleName, violations);
    }

    /** Creates a {@link Violated} result from a single violation. */
    static RuleResult violated(String ruleName, RuleViolation violation) {
        return violated(ruleName, List.of(violation));
    }

    // -------------------------------------------------------------------------
    // Query helpers — allow consumers to avoid explicit pattern matching for
    // simple checks.
    // -------------------------------------------------------------------------

    default boolean isCompliant() {
        return this instanceof Compliant;
    }

    default boolean isViolated() {
        return this instanceof Violated;
    }

    /**
     * Returns the list of violations, or an empty list if the result is compliant.
     * Avoids callers needing to cast when they just want to iterate violations.
     */
    default List<RuleViolation> violations() {
        return switch (this) {
            case Compliant ignored -> List.of();
            case Violated v        -> v.violations();
        };
    }
}
