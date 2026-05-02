package com.tesco.restriction.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a customer's shopping basket containing one or more {@link CartItem}s.
 *
 * <p>Built as an immutable record; the item list is defensively copied to prevent
 * external mutation.  Provides convenience aggregation helpers that rule implementations
 * can use without re-computing them on every call.
 *
 * @param orderId  unique identifier for the order / basket session
 * @param items    the line items in the basket (at least one required)
 */
public record ShoppingCart(String orderId, List<CartItem> items) {

    /** Compact constructor — validates and defensively copies the item list. */
    public ShoppingCart {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be null or blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Shopping cart must contain at least one item");
        }
        items = List.copyOf(items);   // immutable snapshot
    }

    // -------------------------------------------------------------------------
    // Aggregation helpers (computed on demand; rules should call these rather
    // than re-streaming the item list themselves).
    // -------------------------------------------------------------------------

    /**
     * Returns the total quantity across all items for a given category (case-insensitive).
     *
     * @param category the product category to aggregate
     * @return total quantity for that category, or 0 if not present
     */
    public int totalQuantityForCategory(String category) {
        String targetCategory = category.toLowerCase();
        return items.stream()
                .filter(item -> item.categoryLower().equals(targetCategory))
                .mapToInt(CartItem::quantity)
                .sum();
    }

    /**
     * Returns a map of category (lower-cased) → total quantity across all items.
     * Useful for rules that need to inspect multiple categories at once.
     */
    public Map<String, Integer> quantityByCategory() {
        return items.stream()
                .collect(Collectors.groupingBy(
                        CartItem::categoryLower,
                        Collectors.summingInt(CartItem::quantity)
                ));
    }

    /**
     * Returns the total number of item lines (not the sum of quantities).
     */
    public int lineItemCount() {
        return items.size();
    }

    /**
     * Returns the grand total of all item quantities in the basket.
     */
    public int totalQuantity() {
        return items.stream().mapToInt(CartItem::quantity).sum();
    }

    @Override
    public String toString() {
        return "ShoppingCart{orderId='%s', items=%s}".formatted(orderId, items);
    }
}
