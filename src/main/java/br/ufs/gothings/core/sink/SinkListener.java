package br.ufs.gothings.core.sink;

/**
 * @author Wagner Macedo
 */
public interface SinkListener<T> {
    void onSend(SinkEvent<T> event) throws Exception;
}
