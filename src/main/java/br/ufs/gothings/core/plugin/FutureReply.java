package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.message.GwReply;

import java.util.concurrent.Future;

/**
 * @author Wagner Macedo
 */
public interface FutureReply extends Future<GwReply> {
    void setListener(ReplyListener replyListener);
}
