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
    private final ExecutorService executor;
    private final Disruptor<SinkEvent<T>> disruptor;
    private final RingBuffer<SinkEvent<T>> ringBuffer;

    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final DisruptorSinkEventHandler<T> eventHandler;

    @SuppressWarnings("unchecked")
    public Sink() {
        eventHandler = new DisruptorSinkEventHandler<>();

        executor = Executors.newSingleThreadExecutor();
        disruptor = new Disruptor<>(new DisruptorSinkEventFactory<>(), 1024, executor);
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

    public T receive(long sequence, int timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        checkStart();
        final SinkEvent<T> event = ringBuffer.get(sequence);
        event.waitSignal(timeout, unit);

        return event.pull();
    }

    public void setHandler(SinkHandler<T> handler) {
        Objects.requireNonNull(handler);
        eventHandler.t_handler = handler;
        disruptor.start();
        startLatch.countDown();
    }

    public void stop() {
        executor.shutdown();
        disruptor.shutdown();
    }

    private void checkStart() {
        try {
            if (!startLatch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("sink has not a handler");
            }
        } catch (InterruptedException e) {
            // Unlikely to happen
        }
    }

    private static final class DisruptorSinkEventHandler<T> implements EventHandler<SinkEvent<T>> {
        private SinkHandler<T> t_handler;

        @Override
        public void onEvent(final SinkEvent<T> event, long sequence, boolean endOfBatch) throws Exception {
            try {
                t_handler.readEvent(event);
            }
            // At any error assure event is signalized, but value is annulled
            catch (Exception e) {
                event.pushAndSignal(null);
            }
        }
    }

    private static final class DisruptorSinkEventFactory<T> implements EventFactory<SinkEvent<T>> {
        @Override
        public SinkEvent<T> newInstance() {
            return new SinkEvent<>();
        }
    }
}
