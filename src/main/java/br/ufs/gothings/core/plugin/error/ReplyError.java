package br.ufs.gothings.core.plugin.error;

import br.ufs.gothings.core.message.GwRequest;

/**
 * @author Wagner Macedo
 */
public final class ReplyError extends Throwable {
    private final GwRequest request;
    private final Reason reason;

    public ReplyError(final GwRequest request, final Reason reason) {
        this.request = request;
        this.reason = reason;
    }

    public GwRequest getRequest() {
        return request;
    }

    public Reason getReason() {
        return reason;
    }
}
