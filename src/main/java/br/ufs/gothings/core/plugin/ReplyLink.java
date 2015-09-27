package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.plugin.error.ReplyError;

/**
 * @author Wagner Macedo
 */
public interface ReplyLink {
    void send(GwReply reply);

    void error(ReplyError e);
}
