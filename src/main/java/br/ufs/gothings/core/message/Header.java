package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public class Header<T> {
    private T value;

    public void setValue(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }
}
