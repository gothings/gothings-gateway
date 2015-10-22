package br.ufs.gothings.core.message.headers;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Wagner Macedo
 */
public class HKeyMulti<T> extends HKey<T> {
    HKeyMulti(final Class<T> cls, final Predicate<T> validator, final Supplier<Collection<T>> supplier) {
        super(cls, validator, supplier);
    }
}
