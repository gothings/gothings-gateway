package br.ufs.gothings.core.message.headers;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Wagner Macedo
 */
public class HeaderNames {
    public static final HKey<Operation>
            GW_OPERATION = newKey(Operation.class);

    public static final HKey<String>
            GW_TARGET       = newKey(String.class),
            GW_PATH         = newKey(String.class),
            GW_CONTENT_TYPE = newKey(String.class);

    public static final HKeyMulti<String>
            GW_EXPECTED_TYPES = newComplexKey(String.class, LinkedHashSet::new);

    public static final HKey<Integer>
            GW_QOS = newKey(int.class);

    public static final HKey<String>
            GW_CACHE_SIGNATURE = newKey(String.class);

    public static final HKey<Date>
            GW_CACHE_EXPIRATION = newKey(Date.class);

    public static final HKey<Boolean>
            GW_CACHE_MODIFIED = newKey(boolean.class);

    /* Internal use */

    private static <T> HKey<T> newKey(final Class<T> cls) {
        return newKey(cls, null);
    }

    private static <T> HKey<T> newKey(final Class<T> cls, Predicate<T> validator) {
        return new HKey<>(cls, validator);
    }

    private static <T> HKeyMulti<T> newComplexKey(final Class<T> cls, final Supplier<Collection<T>> supplier) {
        return newComplexKey(cls, null, supplier);
    }

    private static <T> HKeyMulti<T> newComplexKey(final Class<T> cls, Predicate<T> validator, final Supplier<Collection<T>> supplier) {
        return new HKeyMulti<>(cls, validator, supplier);
    }
}
