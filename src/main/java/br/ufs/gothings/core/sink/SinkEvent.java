package br.ufs.gothings.core.sink;

import java.util.concurrent.*;

/**
 * @author Wagner Macedo
 */
public final class SinkEvent<T> {
    private final CountDownLatch latch = new CountDownLatch(1);
    private T value;

    public T pull() {
        return value;
    }

    public void pushAndSignal(T value) {
        this.value = value;
        latch.countDown();
    }

    void setValue(T value) {
        this.value = value;
    }

    void waitSignal(int timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (!latch.await(timeout, unit)) {
            throw new TimeoutException();
        }
    }
}
