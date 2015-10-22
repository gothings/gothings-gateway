package br.ufs.gothings.core.message.headers;

import br.ufs.gothings.core.common.ReadOnlyException;
import br.ufs.gothings.core.util.AbstractKey;
import br.ufs.gothings.core.util.CollectionUtils;

import java.util.*;

/**
 * @author Wagner Macedo
 */
public class GwHeaders {
    public static final GwHeaders EMPTY = new GwHeaders().readOnly();

    // Map of header values
    private final Map<HKey, Object> map = new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(HKey<T> key) {
        return key instanceof HKeyMulti
                ? CollectionUtils.firstElement((Collection<T>) map.get(key))
                : (T) map.get(key);
    }

    public <T> Collection<T> getAll(HKeyMulti<T> key) {
        @SuppressWarnings("unchecked")
        final Collection<T> values = (Collection<T>) map.get(key);
        return values != null ? Collections.unmodifiableCollection(values) : Collections.emptyList();
    }

    public <T> void set(HKey<T> key, T value) {
        if (readonly) throw new ReadOnlyException();

        if (value == null) {
            map.computeIfPresent(key, (k, o) -> {
                if (o instanceof Collection)
                    ((Collection) o).clear();
                return null;
            });
        }

        else if (!key.validate(value)) {
            throw new IllegalArgumentException("value `" + value + "` didn't pass in the validation");
        }

        else if (key instanceof HKeyMulti) {
            final Collection<T> collection = getCollection((HKeyMulti<T>) key);
            collection.clear();
            collection.add(value);
        }

        else {
            map.put(key, value);
        }
    }

    public <T> void setIfAbsent(HKey<T> key, T value) {
        if (readonly) throw new ReadOnlyException();

        // remove if absent?
        if (value == null)
            return;

        if (!key.validate(value)) {
            throw new IllegalArgumentException("value `" + value + "` didn't pass in the validation");
        }

        else if (key instanceof HKeyMulti) {
            final Collection<T> collection = getCollection((HKeyMulti<T>) key);
            if (collection.isEmpty()) {
                collection.add(value);
            }
        }

        else {
            map.putIfAbsent(key, value);
        }
    }

    public <T> void add(HKeyMulti<T> key, T value) {
        if (readonly) throw new ReadOnlyException();

        Objects.requireNonNull(value, "value");
        getCollection(key).add(value);
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<T> getCollection(final HKeyMulti<T> key) {
        return (Collection<T>) map.computeIfAbsent(key, AbstractKey::newCollection);
    }

    private volatile boolean readonly = false;

    public final GwHeaders readOnly() {
        readonly = true;
        return this;
    }
}
