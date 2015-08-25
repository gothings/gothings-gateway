package br.ufs.gothings.core.sink;

/**
 * @author Wagner Macedo
 */
public interface SinkLink<T> {
    void send(T value);

    void setListener(SinkListener<T> listener);
}
