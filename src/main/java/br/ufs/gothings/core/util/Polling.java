package br.ufs.gothings.core.util;

import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.GwHeaders;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static br.ufs.gothings.core.message.headers.HeaderNames.GW_PATH;
import static br.ufs.gothings.core.message.headers.HeaderNames.GW_TARGET;

/**
 * @author Wagner Macedo
 */
public final class Polling {
    private final int period;
    private final TimeUnit unit;

    private ConcurrentSkipListSet<Destination> destinations;
    private ScheduledExecutorService scheduler;
    private Runnable runnable;

    private final Consumer<Destination> requestLogic;

    public Polling(final Consumer<Destination> requestLogic, final int period, final TimeUnit unit) {
        this.requestLogic = requestLogic;
        this.period = period;
        this.unit = unit;
    }

    public void start() {
        destinations = new ConcurrentSkipListSet<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        runnable = () -> destinations.forEach(requestLogic);
        scheduler.scheduleAtFixedRate(runnable, 0, period, unit);
    }

    public void stop() {
        scheduler.shutdown();
        destinations = null;
        scheduler = null;
        runnable = null;
    }

    public void add(final GwRequest request) {
        final Destination dst = new Destination(request);
        scheduler.submit(() -> requestLogic.accept(dst));

        if (!destinations.contains(dst)) {
            dst.request = new GwRequest(request.headers(), request.payload());
            dst.request.setSequence(0);
            destinations.add(dst);
        }
    }

    public void del(final GwRequest request) {
        destinations.remove(new Destination(request));
    }

    public static final class Destination implements Comparable {
        private GwRequest request;

        private final String target;
        private final String path;

        public Destination(final GwRequest request) {
            final GwHeaders h = request.headers();
            this.target = h.get(GW_TARGET);
            this.path = h.get(GW_PATH);
            this.request = request;
        }

        public GwRequest getRequest() {
            return request;
        }

        @Override
        public boolean equals(final Object o) {
            try {
                return compareTo(o) == 0;
            } catch (ClassCastException | ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }

        @Override
        public int compareTo(final Object o) {
            if (o instanceof Destination) {
                return compareTo((Destination) o);
            }
            if (o instanceof GwRequest) {
                final GwHeaders h = ((GwRequest) o).headers();
                return compareTo(h.get(GW_TARGET), h.get(GW_PATH));
            }
            throw new ClassCastException("Not a Destination nor GwRequest");
        }

        public int compareTo(final Destination o) {
            return compareTo(o.target, o.path);
        }

        private int compareTo(final String... arr) {
            int cmp = target.compareTo(arr[0]);
            return cmp != 0 ? cmp : path.compareTo(arr[1]);
        }
    }
}
