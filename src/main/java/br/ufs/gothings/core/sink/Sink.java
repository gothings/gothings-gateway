package br.ufs.gothings.core.sink;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.concurrent.*;

/**
 * @author Wagner Macedo
 */
public class Sink<T> {
    private final Disruptor<SinkEvent<T>> disruptor;
    private final RingBuffer<SinkEvent<T>> ringBuffer;

    private final SinkEventHandler<T> eventHandler;
    private final ExecutorService handlerExecutor = Executors.newSingleThreadExecutor();

    @SuppressWarnings("unchecked")
    public Sink(Executor executor) {
        eventHandler = new SinkEventHandler<>(handlerExecutor);

        disruptor = new Disruptor<>(new SinkEventFactory<>(), 1024, executor);
        disruptor.handleEventsWith(eventHandler);
        disruptor.start();

        ringBuffer = disruptor.getRingBuffer();
    }

    public long send(T value) {
        final long sequence = ringBuffer.next();
        final SinkEvent<T> evt = ringBuffer.get(sequence);

        evt.setValue(value);

        ringBuffer.publish(sequence);
        return sequence;
    }

    public T receive(long sequence) throws InterruptedException {
        final SinkEvent<T> event = ringBuffer.get(sequence);
        event.waitSignal();

        return event.getValue();
    }

    public T receive(long sequence, int timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        final SinkEvent<T> event = ringBuffer.get(sequence);
        event.waitSignal(timeout, unit);

        return event.getValue();
    }

    public void setListener(SinkListener<T> listener) {
        eventHandler.listener = listener;
    }

    public void stop() {
        disruptor.shutdown();
        handlerExecutor.shutdown();
    }

    private static final class SinkEvent<T> {
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

    private static final class SinkEventHandler<T> implements EventHandler<SinkEvent<T>> {
        private final ExecutorService executor;
        private SinkListener<T> listener;

        SinkEventHandler(ExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void onEvent(SinkEvent<T> event, long sequence, boolean endOfBatch) throws Exception {
            executor.execute(() -> {
                T value = null;
                try {
                    value = listener.onSend(event.getValue());
                } finally {
                    event.setValue(value);
                    event.signalize();
                }
            });
        }
    }

    private static final class SinkEventFactory<T> implements EventFactory<SinkEvent<T>> {
        @Override
        public SinkEvent<T> newInstance() {
            return new SinkEvent<>();
        }
    }
}
