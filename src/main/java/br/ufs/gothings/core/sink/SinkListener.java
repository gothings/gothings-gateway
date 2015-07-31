package br.ufs.gothings.core.sink;

/**
 * @author Wagner Macedo
 */
public interface SinkListener<T> {
    T onSend(T value);
}
