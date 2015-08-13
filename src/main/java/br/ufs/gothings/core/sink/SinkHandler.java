package br.ufs.gothings.core.sink;

/**
 * @author Wagner Macedo
 */
public interface SinkHandler<T> {
    void readEvent(SinkEvent<T> event) throws Exception;
}
