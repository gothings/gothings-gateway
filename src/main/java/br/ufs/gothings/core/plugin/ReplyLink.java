package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwReply;

/**
 * @author Wagner Macedo
 */
public interface ReplyLink {
    void send(GwReply reply);

    void sendError(GwError error);
}
