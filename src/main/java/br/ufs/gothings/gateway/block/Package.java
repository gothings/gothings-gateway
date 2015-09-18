package br.ufs.gothings.gateway.block;

import br.ufs.gothings.core.GwMessage;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Wagner Macedo
 */
public class Package {
    private final GwMessage message;

    // Extra info
    private String sourceProtocol;
    private String targetProtocol;

    private final Map<Token, Integer> authTokens = Collections.synchronizedMap(new IdentityHashMap<>(2));
    private final Token mainToken;
    private final ExtraInfo extraInfo;

    private final AtomicInteger passes = new AtomicInteger(0);

    public Package(final GwMessage message, final Token mainToken) {
        this.message = message;
        this.mainToken = mainToken;
        this.authTokens.put(mainToken, ExtraInfo.ALL);
        this.extraInfo = new ExtraInfo(this, mainToken);
    }

    public GwMessage getMessage() {
        return message;
    }

    public ExtraInfo getExtraInfo(Token authToken) {
        if (authToken == mainToken) {
            return extraInfo;
        } else {
            return new ExtraInfo(this, authToken);
        }
    }

    public boolean isMainToken(Token authToken) {
        return authToken == mainToken;
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

    public static final class ExtraInfo {
        public static final int ALL             = 0b11111111;

        public static final int SOURCE_PROTOCOL = 0b00000001;
        public static final int TARGET_PROTOCOL = 0b00000010;

        private final Package pkg;
        private final int authMask;

        private ExtraInfo(Package pkg, Token authToken) {
            this.pkg = pkg;
            this.authMask = pkg.authTokens.getOrDefault(authToken, 0);
        }

        public String getSourceProtocol() {
            return pkg.sourceProtocol;
        }

        public void setSourceProtocol(final String sourceProtocol) {
            checkAuth(SOURCE_PROTOCOL);
            pkg.sourceProtocol = sourceProtocol;
        }

        public String getTargetProtocol() {
            return pkg.targetProtocol;
        }

        public void setTargetProtocol(final String targetProtocol) {
            checkAuth(TARGET_PROTOCOL);
            pkg.targetProtocol = targetProtocol;
        }

        private void checkAuth(final int fieldMask) {
            if ((authMask & fieldMask) != fieldMask) {
                throw new IllegalStateException("token not authorized");
            }
        }

        public void addToken(final Token authToken, final int authMask) {
            checkAuth(ALL);
            pkg.authTokens.put(authToken, authMask);
        }
    }
}
