package br.ufs.gothings.core;

import br.ufs.gothings.core.common.AbstractKey;
import br.ufs.gothings.core.message.headers.Operation;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Wagner Macedo
 */
public class GwHeaders {
    /* Header identifiers */

    public static final Key<Operation>
            OPERATION       = newKey(Operation.class);
    public static final Key<String>
            SOURCE          = newKey(String.class),
            TARGET          = newKey(String.class),
            PATH            = newKey(String.class),
            CONTENT_TYPE    = newKey(String.class);
    public static final Key<Integer>
            QOS             = newKey(Integer.class);
    public static final ComplexKey<String>
            EXPECTED_TYPES  = newComplexKey(String.class, LinkedHashSet::new);

    // Map of header values
    private final Map<Key, Object> map = new IdentityHashMap<>();

    /* Header methods */

    @SuppressWarnings("unchecked")
    public synchronized <T> T get(Key<T> key) {
        if (key instanceof ComplexKey) {
            final Collection<T> values = (Collection<T>) map.get(key);
            if (values != null && values.size() > 0) {
                return values.iterator().next();
            }
            return null;
        } else {
            return (T) map.get(key);
        }
    }

    public synchronized <T> void set(Key<T> key, T value) {
        if (value == null) {
            final Object removed = map.remove(key);
            if (removed instanceof Collection) {
                ((Collection) removed).clear();
            }
        }

        else if (!key.validate(value)) {
            throw new IllegalArgumentException("value `" + value + "` didn't pass in the validation");
        }

        else if (key instanceof ComplexKey) {
            final Collection<T> collection = getCollection((ComplexKey<T>) key);
            collection.clear();
            collection.add(value);
        }

        else {
            map.put(key, value);
        }
    }

    public synchronized <T> void add(ComplexKey<T> key, T value) {
        if (value != null) {
            final Collection<T> collection = getCollection(key);
            collection.add(value);
        }
    }

    /* Internal use */

    @SuppressWarnings("unchecked")
    private synchronized <T> Collection<T> getCollection(final ComplexKey<T> key) {
        Collection<T> collection = (Collection<T>) map.get(key);
        if (collection == null) {
            collection = key.newCollection();
            map.put(key, collection);
        }
        return collection;
    }

    /* Key own classes */

    public static class Key<T> extends AbstractKey<Void, T> {
        private Key(final Class<T> cls, final Function<T, Boolean> function) {
            this(cls, function, null);
        }

        private Key(final Class<T> cls, final Function<T, Boolean> function, final Supplier<Collection<T>> supplier) {
            super(null, cls, function, supplier);
        }
    }

    public static class ComplexKey<T> extends Key<T> {
        private ComplexKey(final Class<T> cls, final Function<T, Boolean> function, final Supplier<Collection<T>> supplier) {
            super(cls, function, supplier);
        }
    }

    /* Internal use */

    private static <T> Key<T> newKey(final Class<T> cls) {
        return newKey(cls, null);
    }

    private static <T> Key<T> newKey(final Class<T> cls, Function<T, Boolean> validator) {
        return new Key<>(cls, validator);
    }

    private static <T> ComplexKey<T> newComplexKey(final Class<T> cls, final Supplier<Collection<T>> supplier) {
        return newComplexKey(cls, null, supplier);
    }

    private static <T> ComplexKey<T> newComplexKey(final Class<T> cls, Function<T, Boolean> validator, final Supplier<Collection<T>> supplier) {
        return new ComplexKey<>(cls, validator, supplier);
    }
}
