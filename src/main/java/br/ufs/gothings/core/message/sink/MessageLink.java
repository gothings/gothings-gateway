package br.ufs.gothings.core.message.sink;

import br.ufs.gothings.core.GwMessage;

import java.util.concurrent.Future;

/**
 * @author Wagner Macedo
 */
public interface MessageLink {
    Future<GwMessage> send(GwMessage msg);

    void setUp(MessageListener listener);
}
