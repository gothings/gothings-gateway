package br.ufs.gothings.core;

import br.ufs.gothings.core.message.MessageType;
import br.ufs.gothings.core.message.Payload;

/**
 * @author Wagner Macedo
 */
public abstract class GwMessage {
    protected MessageType type;

    private final GwHeaders headers;
    private final Payload payload;

    protected GwMessage(GwHeaders headers, Payload payload) {
        this.headers = headers;
        this.payload = payload;
    }

    public final GwHeaders headers() {
        return headers;
    }

    public final Payload payload() {
        return payload;
    }

    public final MessageType getType() {
        return type;
    }
}
