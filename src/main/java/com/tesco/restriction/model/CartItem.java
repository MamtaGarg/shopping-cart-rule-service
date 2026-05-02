package com.tesco.restriction.model;

/**
 * Represents a single item in a shopping cart.
 *
 * <p>Immutable record leveraging Java 21 features. The category comparison is
 * case-insensitive to avoid data quality issues (e.g. "Paracetamol" vs "paracetamol").
 *
 * @param productId  unique identifier for the product
 * @param category   product category (e.g. "Paracetamol", "analgesic", "chocolate")
 * @param quantity   number of units added to the basket
 */
public record CartItem(String productId, String category, int quantity) {

    /** Compact constructor — validates invariants at construction time. */
    public CartItem {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be null or blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be null or blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive, got %d for productId=%s".formatted(quantity, productId));
        }
        // Normalise to trimmed strings; category comparison is done case-insensitively at rule level
        productId = productId.trim();
        category  = category.trim();
    }

    /**
     * Returns the category in lower-case for consistent comparisons.
     */
    public String categoryLower() {
        return category.toLowerCase();
    }

    @Override
    public String toString() {
        return "CartItem{productId='%s', category='%s', quantity=%d}".formatted(productId, category, quantity);
    }
}
