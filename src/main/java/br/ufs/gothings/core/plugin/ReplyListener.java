package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwReply;

/**
 * @author Wagner Macedo
 */
public interface ReplyListener {
    void onReply(GwReply reply);

    void onError(GwError error);
}
