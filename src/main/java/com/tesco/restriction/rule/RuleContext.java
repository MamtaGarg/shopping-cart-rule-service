package com.tesco.restriction.rule;

import com.tesco.restriction.model.ShoppingCart;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the runtime context that is passed to every {@link RestrictionRule}.
 *
 * <p>Separating the context from the cart model allows the engine to attach
 * execution-time metadata (e.g. customer tier, store location, feature flags) without
 * polluting the domain model — making it straightforward to add context attributes in
 * future without changing any rule signatures.
 *
 * @param cart     the shopping basket being evaluated
 * @param metadata key/value bag for any extra runtime attributes rules may need
 */
public record RuleContext(ShoppingCart cart, Map<String, Object> metadata) {

    /** Compact constructor — defensively copies the metadata map. */
    public RuleContext {
        if (cart == null) {
            throw new IllegalArgumentException("cart must not be null");
        }
        metadata = metadata != null
                ? Collections.unmodifiableMap(new HashMap<>(metadata))
                : Collections.emptyMap();
    }

    /** Convenience constructor with no extra metadata. */
    public RuleContext(ShoppingCart cart) {
        this(cart, Collections.emptyMap());
    }

    /**
     * Returns a typed metadata value, or {@code defaultValue} if absent / wrong type.
     *
     * @param key          metadata key
     * @param type         expected type
     * @param defaultValue fallback value
     * @param <T>          target type
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type, T defaultValue) {
        Object value = metadata.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }
}
