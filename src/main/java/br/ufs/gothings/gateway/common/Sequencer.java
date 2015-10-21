package br.ufs.gothings.gateway.common;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Wagner Macedo
 */
public class Sequencer {
    private static final long NORMAL_INIT = 1L << 32;
    private static final int OBSERVE_INIT = 0;

    private final AtomicLong normalSequence = new AtomicLong(NORMAL_INIT);
    private final AtomicLong observeSequence = new AtomicLong(OBSERVE_INIT);

    public long nextNormal() {
        return normalSequence.getAndUpdate(seq -> ++seq == 0 ? NORMAL_INIT : seq);
    }

    public long nextObserve() {
        return observeSequence.getAndUpdate(seq -> ++seq == NORMAL_INIT ? OBSERVE_INIT : seq);
    }

    public static boolean isObserve(final long sequence) {
        return sequence < NORMAL_INIT;
    }
}
