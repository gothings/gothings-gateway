package br.ufs.gothings.gateway;

import br.ufs.gothings.core.common.GatewayException;
import br.ufs.gothings.core.common.Reason;
import br.ufs.gothings.core.message.headers.GwHeaders;
import br.ufs.gothings.core.message.GwMessage;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.gateway.common.Controller;
import br.ufs.gothings.gateway.common.Package;
import br.ufs.gothings.gateway.common.Sequencer;
import br.ufs.gothings.gateway.common.StopProcessException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static br.ufs.gothings.core.message.headers.HeaderNames.GW_OPERATION;
import static br.ufs.gothings.core.message.headers.HeaderNames.GW_PATH;
import static br.ufs.gothings.core.message.headers.HeaderNames.GW_TARGET;

/**
 * @author Wagner Macedo
 */
public class InterconnectionController implements Controller {
    private static final Logger logger = LogManager.getFormatterLogger(InterconnectionController.class);

    private final ObserveList observeList = new ObserveList();

    @Override
    public void process(final Package pkg) throws Exception {
        final GwMessage message = pkg.getMessage();

        if (message instanceof GwRequest) {
            final GwRequest request = (GwRequest) message;
            final GwHeaders headers = request.headers();

            final URI uri;
            try {
                uri = createURI(request);
            } catch (URISyntaxException e) {
                if (logger.isErrorEnabled()) {
                    logger.error("could not parse URI from path sent by %s plugin: %s",
                            pkg.getSourceProtocol(), e.getInput());
                }
                throw new GatewayException(request, Reason.INVALID_URI);
            }

            final String targetProtocol = uri.getScheme();
            pkg.setTargetProtocol(targetProtocol);

            final String target = uri.getRawAuthority();
            headers.set(GW_TARGET, target);

            final String targetAndPath = uri.getRawSchemeSpecificPart();
            final String path = StringUtils.replaceOnce(targetAndPath, "//" + target, "");
            headers.set(GW_PATH, path);

            final Operation operation = headers.get(GW_OPERATION);
            final String s_uri = uri.toString();

            final GwReply cached = getCache(operation, s_uri);
            if (cached != null) {
                cached.setSequence(request.getSequence());
                pkg.setMessage(cached);
            } else {
                switch (operation) {
                    case READ:
                    case OBSERVE:
                        observeList.add(s_uri, pkg.getSourceProtocol(), request.getSequence());
                        break;
                    case UNOBSERVE:
                        if (!observeList.remove(s_uri, pkg.getSourceProtocol(), request.getSequence())) {
                            throw new StopProcessException();
                        }
                        break;
                }
            }
        }

        else if (message instanceof GwReply) {
            final GwReply reply = (GwReply) message;
            final String sourceProtocol = pkg.getSourceProtocol();

            try {
                // Make uri and reply path
                final URI uri = createURI(reply, sourceProtocol);
                reply.headers().set(GW_PATH, "/" + uri.toString().replaceFirst(":/", ""));

                final Map<String, long[]> observers = observeList.get(uri.toString());
                pkg.setReplyTo(observers);
            } catch (URISyntaxException e) {
                if (logger.isErrorEnabled()) {
                    logger.error("error on assembling URI from reply of %s plugin: %s",
                            sourceProtocol, e.getInput());
                }
                throw new StopProcessException();
            }

            setCache(reply, sourceProtocol);
        }
    }

    private void setCache(final GwReply reply, final String protocol) {

    }

    private GwReply getCache(final Operation operation, final String uri) {
        if (operation != Operation.READ) {
            return null;
        }

        // TODO: Method stub
        return null;
    }

    private URI createURI(final GwRequest msg) throws URISyntaxException {
        final String path = msg.headers().get(GW_PATH);
        final String s_uri = path.replaceFirst("^/+", "").replaceFirst("/+", "://");
        final URIBuilder uri = new URIBuilder(s_uri);

        // sort query parameters
        final List<NameValuePair> params = uri.getQueryParams();
        if (!params.isEmpty()) {
            params.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
            uri.setParameters(params);
        }

        return uri.build();
    }

    private URI createURI(final GwReply msg, final String sourceProtocol) throws URISyntaxException {
        final String target = msg.headers().get(GW_TARGET);
        final String path = msg.headers().get(GW_PATH).replaceFirst("^/+", "");

        return new URI(String.format("%s://%s/%s", sourceProtocol, target, path));
    }

    public ObserveList getObserveList() {
        return observeList;
    }

    /**
     * Mapping of observing sequences to reply
     * <p>
     * Each observing is in the form: {@code [(uri, protocol, sequences)] where:
     * <ul>
     * <li>{@code uri} is a string used as the observing filter.
     * <li>{@code protocol} is the name of the interested protocol as registered by the plugin.
     * <li>{@code sequences} is a collection of message sequences to reply, this is useful for
     *      request/response protocols. If sequences is {@code null} the reply is sent to the
     *      plugin every time the gateway receives a reply with this filter.
     * </ul>
     */
    static class ObserveList {
        private final Map<String, Map<String, Set<Long>>> map = new ConcurrentHashMap<>();
        private final Lock lock = new ReentrantLock();

        private void add(final String uri, final String protocol, final long sequence) {
            final Map<String, Set<Long>> uriObserving = map.computeIfAbsent(uri, k -> new HashMap<>());
            lock.lock();
            final Set<Long> sequences = uriObserving.computeIfAbsent(protocol, k -> new HashSet<>());
            sequences.add(sequence);
            lock.unlock();
        }

        private Map<String, long[]> get(final String uri) {
            final Map<String, Set<Long>> uriObserving = map.get(uri);
            if (uriObserving != null) {
                Map<String, long[]> result = new HashMap<>();
                lock.lock();
                try {
                    for (final String key : uriObserving.keySet()) {
                        uriObserving.compute(key, (k, sequences) -> {
                            final long[] array = new long[sequences.size()];
                            result.put(key, array);

                            final Iterator<Long> it = sequences.iterator();
                            int i = 0;
                            while (it.hasNext()) {
                                final long next = it.next();
                                array[i++] = next;
                                if (!Sequencer.isObserve(next)) {
                                    it.remove();
                                }
                            }

                            if (sequences.isEmpty()) {
                                return null;
                            } else {
                                return sequences;
                            }
                        });
                    }
                    return result;
                } finally {
                    lock.unlock();
                }
            }
            throw new NoSuchElementException("no sequence observing " + uri);
        }

        /**
         * Remove the following observed sequence
         *
         * @param uri         the uri to find
         * @param protocol    the protocol associated to uri
         * @param sequence    the sequence associated to both uri and protocol
         * @return true if list of sequences was left empty, false otherwise.
         */
        private boolean remove(final String uri, final String protocol, final long sequence) {
            final Map<String, Set<Long>> uriObserving = map.get(uri);
            if (uriObserving != null) {
                lock.lock();
                try {
                    final Set<Long> sequences = uriObserving.get(protocol);
                    if (sequences != null) {
                        sequences.remove(sequence);
                        if (sequences.isEmpty()) {
                            uriObserving.remove(protocol);
                            return true;
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
            return true;
        }

        /**
         * Remove the sequence from the observing list.
         *
         * @param sequence    sequence to be removed
         */
        public void remove(final long sequence) {
            lock.lock();
            try {
                for (final Entry<String, Map<String, Set<Long>>> uriEntry : map.entrySet()) {
                    for (final Entry<String, Set<Long>> obsEntry : uriEntry.getValue().entrySet()) {
                        if (obsEntry.getValue().remove(sequence)) {
                            return;
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
