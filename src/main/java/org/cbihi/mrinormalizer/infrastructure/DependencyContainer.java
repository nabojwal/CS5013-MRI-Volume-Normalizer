package org.cbihi.mrinormalizer.infrastructure;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Simple dependency injection container for the MRI Volume Normalizer.
 */
public class DependencyContainer {

    private final Map<Class<?>, Object> singletons = new HashMap<>();

    /**
     * Registers a singleton instance for the given type.
     *
     * @param type the dependency type
     * @param instance the singleton instance
     * @param <T> the dependency type
     */
    public <T> void register(Class<T> type, T instance) {
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(instance, "Instance cannot be null");
        this.singletons.put(type, instance);
    }

    /**
     * Retrieves an instance of the specified type from the container.
     *
     * @param type the dependency type
     * @param <T> the dependency type
     * @return the registered instance, or null if none is registered
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Objects.requireNonNull(type, "Type cannot be null");
        return (T) this.singletons.get(type);
    }

    /**
     * Checks if an instance of the specified type is registered.
     *
     * @param type the dependency type
     * @return true if the type is registered, otherwise false
     */
    public boolean contains(Class<?> type) {
        Objects.requireNonNull(type, "Type cannot be null");
        return this.singletons.containsKey(type);
    }
}