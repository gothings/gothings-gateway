package br.ufs.gothings.core.message.sink;

import br.ufs.gothings.core.GwMessage;

/**
 * @author Wagner Macedo
 */
public interface MessageListener {
    void valueReceived(GwMessage msg) throws Exception;
}