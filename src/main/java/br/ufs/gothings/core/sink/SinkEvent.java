package br.ufs.gothings.core.sink;

import java.util.concurrent.*;

/**
 * @author Wagner Macedo
 */
public final class SinkEvent<T> {
    private final CountDownLatch latch = new CountDownLatch(1);
    private T value;
    private Executor jobExecutor;
    private Runnable job;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public void asyncJob(Runnable job) {
        this.job = job;
    }

    public void asyncJob(Executor executor, Runnable job) {
        this.jobExecutor = executor;
        this.job = job;
    }

    Runnable asyncJob() {
        return job;
    }

    Executor chooseExecutor(Executor executor) {
        return jobExecutor != null ? jobExecutor : executor;
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
