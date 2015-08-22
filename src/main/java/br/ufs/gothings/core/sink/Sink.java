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

    public SinkLink<T> createLink(SinkHandler<T> handler) {
        final ValueSinkLink sinkLink = new ValueSinkLink(handler);
        eventHandler.addLink(sinkLink);
        return sinkLink;
    }

    private void checkStart() {
        try {
            eventHandler.waitLinks(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // do nothing
        } catch (TimeoutException e) {
            throw new IllegalStateException("sink has not the required two links");
        }
    }

    public void stop() {
        executor.shutdown();
        disruptor.shutdown();
    }

    private final class ValueSinkLink implements SinkLink<T> {
        private final SinkHandler<T> handler;

        ValueSinkLink(SinkHandler<T> handler) {
            this.handler = handler;
        }

        SinkHandler<T> getHandler() {
            return handler;
        }

        @Override
        public long put(T value) {
            Validate.notNull(value);
            checkStart();

            final long sequence = ringBuffer.next();
            final SinkEvent<T> event = ringBuffer.get(sequence);

            event.setLink(this);
            event.setValue(value);

            ringBuffer.publish(sequence);
            return sequence;
        }

        @Override
        public T get(long sequence, int timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            Validate.notNull(unit);
            checkStart();

            final SinkEvent<T> event = ringBuffer.get(sequence);
            event.waitFinish(timeout, unit);

            return event.getValue();
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
                if (link != event.getLink()) {
                    try {
                        link.getHandler().readEvent(event);
                    } catch (Exception e) {
                        // At any error assure event is finished, but value is annulled
                        event.writeValue(null);
                    }
                    break;
                }
            }
        }
    }
}
