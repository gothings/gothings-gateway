package br.ufs.gothings.gateway.block;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.common.AbstractKey;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Wagner Macedo
 */
public class Forwarding {
    private final GwMessage message;

    private final Map<InfoName, Entry<Set<Token>, Object>> extraInfo =
            Collections.synchronizedMap(new IdentityHashMap<>(2));

    private final Token mainToken;
    private final AtomicInteger passes = new AtomicInteger(0);

    public Forwarding(GwMessage message, final Token mainToken) {
        this.message = message;
        this.mainToken = mainToken;
    }

    public GwMessage getMessage() {
        return message;
    }

    @SuppressWarnings("unchecked")
    public <T> T getExtraInfo(InfoName<T> infoName) {
        final Entry<Set<Token>, Object> entry = extraInfo.get(infoName);
        return entry != null ? (T) entry.getValue() : null;
    }

    public <T> void setExtraInfo(Token token, InfoName<T> infoName, T value) {
        Entry<Set<Token>, Object> entry = null;
        if (token != mainToken) {
            entry = getEntry(infoName);
            if (!entry.getKey().contains(token)) {
                throw new IllegalArgumentException("access denied for this token");
            }
        }

        if (entry == null) {
            entry = getEntry(infoName);
        }

        entry.setValue(value);
    }

    public <T> void addExtraInfoToken(Token mainToken, Token newToken, InfoName<T> infoName) {
        if (mainToken != this.mainToken) {
            throw new IllegalArgumentException("only main token can add a token");
        }
        getEntry(infoName).getKey().add(newToken);
    }

    private <T> Entry<Set<Token>, Object> getEntry(final InfoName<T> infoName) {
        return extraInfo.computeIfAbsent(infoName, k -> new SimpleEntry<>(new HashSet<>(), null));
    }

    public boolean isMainToken(Token token) {
        return token == mainToken;
    }

    public int getPasses() {
        return passes.get();
    }

    public int pass(final Token token) {
        if (token != this.mainToken) {
            throw new IllegalArgumentException("only main token can increment pass");
        }
        return passes.incrementAndGet();
    }

    public static final class InfoName<T> extends AbstractKey<Void, T> {
        public static final InfoName<String>
                SOURCE_PROTOCOL = new InfoName<>(String.class),
                TARGET_PROTOCOL = new InfoName<>(String.class);

        private InfoName(final Class<T> classType) {
            super(null, classType, null);
        }
    }
}
