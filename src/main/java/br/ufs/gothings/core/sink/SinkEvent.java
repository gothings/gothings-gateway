package br.ufs.gothings.core.sink;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Wagner Macedo
 */
public final class SinkEvent<T> {
    private final CountDownLatch latch = new CountDownLatch(1);
    private T value;

    T getValue() {
        return value;
    }

    void setValue(T value) {
        this.value = value;
    }

    void waitSignal() throws InterruptedException {
        latch.await();
    }

    void waitSignal(int timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (!latch.await(timeout, unit)) {
            throw new TimeoutException();
        }
    }

    void signalize() {
        latch.countDown();
    }
}
