package br.ufs.gothings.core.util;

import java.util.Collection;

/**
 * @author Wagner Macedo
 */
public class CollectionUtils {
    public static <T> T firstElement(final Collection<T> values) {
        if (values != null && values.size() > 0) {
            return values.iterator().next();
        }
        return null;
    }

    public static boolean isEmpty(final Collection values) {
        return values == null || values.isEmpty();
    }
}
