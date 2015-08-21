package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public class Header<T> {
    private T value;

    public void set(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}
