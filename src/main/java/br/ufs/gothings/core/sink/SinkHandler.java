package br.ufs.gothings.core.sink;

/**
 * @author Wagner Macedo
 */
public interface SinkHandler<T> {
    void valueReceived(T value) throws Exception;
}
