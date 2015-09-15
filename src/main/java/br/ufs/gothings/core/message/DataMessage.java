package br.ufs.gothings.core.message;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.Payload;

/**
 * @author Wagner Macedo
 */
public abstract class DataMessage extends GwMessage {

    private final GwHeaders headers;
    private final Payload payload;

    protected DataMessage() {
        this(null, null);
    }

    protected DataMessage(GwHeaders headers, Payload payload) {
        this.headers = (headers != null) ? headers : new GwHeaders();
        this.payload = (payload != null) ? payload : new Payload();
    }

    public final GwHeaders headers() {
        return headers;
    }

    public final Payload payload() {
        return payload;
    }
}
