package br.ufs.gothings.core.common;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Wagner Macedo
 */
public abstract class AbstractKey<K, T> {
    private final K keyId;
    private final Class<T> classType;
    private final Function<T, Boolean> validator;
    private final Supplier<Collection<T>> collectionSupplier;

    protected AbstractKey(final K keyId, final Class<T> classType, final Function<T, Boolean> validator) {
        this(keyId, classType, validator, null);
    }

    protected AbstractKey(final K keyId, final Class<T> classType, final Function<T, Boolean> validator,
                          final Supplier<Collection<T>> collectionSupplier)
    {
        this.keyId = keyId;
        this.classType = classType;
        this.validator = validator;
        this.collectionSupplier = collectionSupplier;
    }

    public final K getKeyId() {
        return keyId;
    }

    public final Class<T> getClassType() {
        return classType;
    }

    public final boolean validate(T value) {
        if (validator == null) {
            return true;
        } else if (!classType.isInstance(value)) {
            throw new ClassCastException("value is not a type of " + classType);
        } else {
            try {
                return validator.apply(value);
            } catch (Exception e) {
                return false;
            }
        }
    }

    public final Collection<T> newCollection() {
        return collectionSupplier != null ? collectionSupplier.get() : null;
    }

    public final boolean isCollection() {
        return collectionSupplier != null;
    }
}
