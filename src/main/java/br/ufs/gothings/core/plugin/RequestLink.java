package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;

import java.util.concurrent.Future;

/**
 * @author Wagner Macedo
 */
public interface RequestLink {
    Future<GwReply> send(GwRequest request);
}
