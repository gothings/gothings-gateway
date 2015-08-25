package br.ufs.gothings.core;

import br.ufs.gothings.core.message.Payload;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Wagner Macedo
 */
public final class GwMessage {
    private static final AtomicLong SEQUENCER = new AtomicLong(0);

    private final GwHeaders headers;
    private final Payload payload;
    private final Long sequence;
    private final boolean answer;

    private GwMessage(GwHeaders headers, Payload payload, Long sequence, boolean answer) {
        this.headers = headers;
        this.payload = payload;
        this.sequence = sequence;
        this.answer = answer;
    }

    private GwMessage(Long sequence, boolean answer) {
        this(new GwHeaders(), new Payload(), sequence, answer);
    }

    private GwMessage() {
        this(SEQUENCER.incrementAndGet(), false);
    }

    public final GwHeaders headers() {
        return headers;
    }

    public final Payload payload() {
        return payload;
    }

    public Long sequence() {
        return sequence;
    }

    public boolean isAnswer() {
        return answer;
    }

    public static GwMessage newMessage() {
        return new GwMessage();
    }

    public static GwMessage newAnswerMessage() {
        return new GwMessage(null, true);
    }

    public static GwMessage newAnswerMessage(Long sequence) {
        return new GwMessage(sequence, true);
    }

    public static GwMessage newAnswerMessage(GwMessage msg) {
        return new GwMessage(msg.headers, msg.payload, msg.sequence, true);
    }
}
