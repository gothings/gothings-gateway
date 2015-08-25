package br.ufs.gothings.core.sink;

/**
 * @author Wagner Macedo
 */
public interface SinkLink<T> {
    void send(T value);

    void setHandler(SinkHandler<T> handler);
}
