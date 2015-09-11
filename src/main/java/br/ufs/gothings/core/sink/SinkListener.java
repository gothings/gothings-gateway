package br.ufs.gothings.core.sink;

import br.ufs.gothings.core.GwMessage;

/**
 * @author Wagner Macedo
 */
public interface SinkListener {
    void valueReceived(GwMessage msg) throws Exception;
}
