package br.ufs.gothings.core.sink;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author Wagner Macedo
 */
public class Sink<T> {
    private final ExecutorService executor;
    private final Disruptor<SinkEvent<T>> disruptor;
    private final RingBuffer<SinkEvent<T>> ringBuffer;

    private final ValueSinkEventHandler eventHandler;

    @SuppressWarnings("unchecked")
    public Sink() {
        eventHandler = new ValueSinkEventHandler();

        executor = Executors.newSingleThreadExecutor();
        disruptor = new Disruptor<>(SinkEvent::new, 1024, executor);
        disruptor.handleEventsWith(eventHandler);
        ringBuffer = disruptor.getRingBuffer();

        disruptor.start();
    }

    public SinkLink<T> createLink() {
        return new ValueSinkLink();
    }

    private void checkStart() {
        try {
            eventHandler.waitLinks(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            // do nothing
        } catch (TimeoutException e) {
            throw new IllegalStateException("sink is not ready yet");
        }
    }

    public void stop() {
        executor.shutdown();
        disruptor.shutdown();
    }

    private static final class SinkEvent<T> {
        private T value;
        private SinkLink<T> sourceLink;

        T getValue() {
            return value;
        }

        void setValue(T value) {
            this.value = value;
        }

        SinkLink<T> getSourceLink() {
            return sourceLink;
        }

        void setSourceLink(SinkLink<T> sourceLink) {
            this.sourceLink = sourceLink;
        }
    }

    private final class ValueSinkLink implements SinkLink<T> {
        private SinkListener<T> listener;

        @Override
        public void send(T value) {
            Validate.notNull(value);
            checkStart();

            final long sequence = ringBuffer.next();
            final SinkEvent<T> event = ringBuffer.get(sequence);

            event.setValue(value);
            event.setSourceLink(this);

            ringBuffer.publish(sequence);
        }

        @Override
        public void setListener(SinkListener<T> listener) {
            Validate.validState(this.listener == null, "listener already set");
            this.listener = listener;
            eventHandler.addLink(this);
        }

        SinkListener<T> getListener() {
            return listener;
        }
    }

    private final class ValueSinkEventHandler implements EventHandler<SinkEvent<T>> {
        private List<ValueSinkLink> sinkLinks = new ArrayList<>(2);
        private final CountDownLatch latch = new CountDownLatch(2);

        void addLink(ValueSinkLink link) {
            Validate.validState(latch.getCount() > 0, "sink cannot have more than two links");

            sinkLinks.add(link);
            latch.countDown();
        }

        void waitLinks(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            if (!latch.await(timeout, unit)) {
                throw new TimeoutException();
            }
        }

        @Override
        public void onEvent(final SinkEvent<T> event, long sequence, boolean endOfBatch) {
            for (ValueSinkLink link : sinkLinks) {
                if (link != event.getSourceLink()) {
                    try {
                        link.getListener().valueReceived(event.getValue());
                    } catch (Exception ignored) {
                        // Any errors are silently ignored
                    }
                    break;
                }
            }
        }
    }
}
