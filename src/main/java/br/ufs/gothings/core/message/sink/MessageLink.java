package br.ufs.gothings.core.message.sink;

import br.ufs.gothings.core.GwMessage;

/**
 * @author Wagner Macedo
 */
public interface MessageLink {
    void send(GwMessage msg);

    void setListener(MessageListener listener);
}
