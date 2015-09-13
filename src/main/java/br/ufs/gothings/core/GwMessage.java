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

    protected GwMessage() {
        this(null, null);
    }

    protected GwMessage(GwHeaders headers, Payload payload) {
        this.headers = (headers != null) ? headers : new GwHeaders();
        this.payload = (payload != null) ? payload : new Payload();
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
