package com.tesco.restriction.model;

/**
 * Indicates whether a restriction rule check has been satisfied.
 *
 * <ul>
 *   <li>{@link #MET}      – the basket is compliant with the rule; no action needed.</li>
 *   <li>{@link #BREACHED} – the basket violates the rule; the order should be blocked
 *                            or the customer prompted to adjust quantities.</li>
 * </ul>
 */
public enum RestrictionStatus {

    /** The rule constraint is satisfied — the basket is within allowed limits. */
    MET,

    /** The rule constraint is violated — the basket exceeds allowed limits. */
    BREACHED;

    /**
     * Returns {@code true} if this status represents a rule violation.
     */
    public boolean isBreached() {
        return this == BREACHED;
    }

    /**
     * Returns {@code true} if this status represents compliance.
     */
    public boolean isMet() {
        return this == MET;
    }
}
