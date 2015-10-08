package br.ufs.gothings.gateway.block;

import br.ufs.gothings.core.message.GwMessage;

import java.util.Map;

/**
 * @author Wagner Macedo
 */
public class Package {
    // Package information
    private GwMessage message;
    private String sourceProtocol;
    private String targetProtocol;
    private Map<String, long[]> replyTo;

    public GwMessage getMessage() {
        return message;
    }

    public void setMessage(final GwMessage message) {
        this.message = message;
    }

    public String getSourceProtocol() {
        return sourceProtocol;
    }

    public void setSourceProtocol(final String sourceProtocol) {
        this.sourceProtocol = sourceProtocol;
    }

    public String getTargetProtocol() {
        return targetProtocol;
    }

    public void setTargetProtocol(final String targetProtocol) {
        this.targetProtocol = targetProtocol;
    }

    public Map<String, long[]> getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(final Map<String, long[]> replyTo) {
        this.replyTo = replyTo;
    }
}
