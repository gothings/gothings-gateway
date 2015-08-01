package br.ufs.gothings.core.sink;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * @author Wagner Macedo
 */
public class Sink<T> {
    private final Disruptor<SinkEvent<T>> disruptor;
    private final RingBuffer<SinkEvent<T>> ringBuffer;
    private final CountDownLatch startLatch = new CountDownLatch(1);

    private final SinkEventHandler<T> eventHandler;
    private final ExecutorService handlerExecutor;

    @SuppressWarnings("unchecked")
    public Sink(Executor executor) {
        handlerExecutor = Executors.newSingleThreadExecutor();
        eventHandler = new SinkEventHandler<>(handlerExecutor);

        disruptor = new Disruptor<>(new SinkEventFactory<>(), 1024, executor);
        disruptor.handleEventsWith(eventHandler);

        ringBuffer = disruptor.getRingBuffer();
    }

    public long send(T value) {
        checkStart();
        final long sequence = ringBuffer.next();
        final SinkEvent<T> evt = ringBuffer.get(sequence);

        evt.setValue(value);

        ringBuffer.publish(sequence);
        return sequence;
    }

    public T receive(long sequence) throws InterruptedException {
        checkStart();
        final SinkEvent<T> event = ringBuffer.get(sequence);
        event.waitSignal();

        return event.getValue();
    }

    public T receive(long sequence, int timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        checkStart();
        final SinkEvent<T> event = ringBuffer.get(sequence);
        event.waitSignal(timeout, unit);

        return event.getValue();
    }

    public void setListener(SinkListener<T> listener) {
        Objects.requireNonNull(listener);
        eventHandler.listener = listener;
        disruptor.start();
        startLatch.countDown();
    }

    public void stop() {
        disruptor.shutdown();
        handlerExecutor.shutdown();
    }

    private void checkStart() {
        try {
            if (!startLatch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("sink has not a listener");
            }
        } catch (InterruptedException e) {
            // Unlikely to happen
        }
    }

    private static final class SinkEventHandler<T> implements EventHandler<SinkEvent<T>> {
        private final ExecutorService executor;
        private SinkListener<T> listener;

        SinkEventHandler(ExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void onEvent(SinkEvent<T> event, long sequence, boolean endOfBatch) throws Exception {
            try {
                listener.onSend(event);
            } catch (RuntimeException e) {
                // At any error assure event is signalized
                event.signalize();
                return;
            }

            // If listener set a job to run asynchronously
            final Runnable job = event.asyncJob();
            if (job != null) {
                executor.execute(() -> {
                    try {
                        job.run();
                    } finally {
                        event.signalize();
                    }
                });
                return;
            }

            // Listener ran (successfully) on current thread
            event.signalize();
        }
    }

    private static final class SinkEventFactory<T> implements EventFactory<SinkEvent<T>> {
        @Override
        public SinkEvent<T> newInstance() {
            return new SinkEvent<>();
        }
    }
}
