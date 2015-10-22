package br.ufs.gothings.core.message.headers;

import br.ufs.gothings.core.util.AbstractKey;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Wagner Macedo
 */
public class HKey<T> extends AbstractKey<Void, T> {
    HKey(final Class<T> cls, final Predicate<T> validator) {
        this(cls, validator, null);
    }

    HKey(final Class<T> cls, final Predicate<T> validator, final Supplier<Collection<T>> supplier) {
        super(null, cls, validator, supplier);
    }
}
