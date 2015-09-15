package br.ufs.gothings.core.common;

import java.util.function.Function;

/**
 * @author Wagner Macedo
 */
public final class Key<T> {
    private final String name;
    private final Class<T> classType;
    private final Function<T, Boolean> validator;

    public Key(final String name, final Class<T> classType, final Function<T, Boolean> validator) {
        this.name = name;
        this.classType = classType;
        this.validator = validator;
    }

    public String getName() {
        return name;
    }

    public Class<T> getClassType() {
        return classType;
    }

    public boolean validate(T value) {
        if (validator == null) {
            return true;
        } else {
            try {
                return validator.apply(value);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
