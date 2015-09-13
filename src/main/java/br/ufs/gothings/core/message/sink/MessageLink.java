package br.ufs.gothings.core.message.sink;

import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;

import java.util.concurrent.Future;

/**
 * @author Wagner Macedo
 */
public interface MessageLink {
    Future<GwReply> sendRequest(GwRequest request);

    void sendReply(GwReply reply);

    void setUp(MessageListener listener);
}
