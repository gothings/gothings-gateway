package br.ufs.gothings.gateway.block;

import br.ufs.gothings.core.GwMessage;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Wagner Macedo
 */
public class Forwarding {
    private final GwMessage message;
    private final Object extraInfo;
    private final AtomicInteger refCount = new AtomicInteger(1);

    public Forwarding(GwMessage message, Object extraInfo) {
        this.message = message;
        this.extraInfo = extraInfo;
    }

    public GwMessage getMessage() {
        return message;
    }

    public Object getExtraInfo() {
        return extraInfo;
    }

    public boolean markUsed() {
        return refCount.decrementAndGet() == 0;
    }
}
