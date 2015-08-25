package br.ufs.gothings.core.sink;

/**
 * @author Wagner Macedo
 */
public interface SinkListener<T> {
    void valueReceived(T value) throws Exception;
}
