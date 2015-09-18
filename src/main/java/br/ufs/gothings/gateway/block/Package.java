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

    private final PackageContext ctx;
    private final ExtraInfo extraInfo;

    private final AtomicInteger passes = new AtomicInteger(0);

    private Package(final GwMessage message, final PackageContext ctx) {
        this.message = message;
        this.ctx = ctx;
        this.extraInfo = new ExtraInfo(this, ctx.mainToken);
    }

    public GwMessage getMessage() {
        return message;
    }

    public ExtraInfo getExtraInfo(final Token authToken) {
        if (authToken == ctx.mainToken) {
            return extraInfo;
        } else {
            return new ExtraInfo(this, authToken);
        }
    }

    public boolean isMainToken(final Token authToken) {
        return authToken == ctx.mainToken;
    }

    public int incrementPass(final Token authToken) {
        if (authToken != ctx.mainToken) {
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
            this.authMask = pkg.ctx.authTokens.getOrDefault(authToken, 0);
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
    }

    public static PackageFactory getFactory(final Token mainToken) {
        return new PackageFactory(mainToken);
    }

    public static class PackageFactory {
        private final PackageContext ctx;

        private PackageFactory(final Token mainToken) {
            ctx = new PackageContext(mainToken);
            ctx.authTokens.put(mainToken, ExtraInfo.ALL);
        }

        public Package newPackage(final GwMessage message) {
            return new Package(message, ctx);
        }

        public void addExtraInfoToken(final Token authToken, final int authMask) {
            ctx.authTokens.put(authToken, authMask);
        }
    }

    private static class PackageContext {
        private final Token mainToken;
        private final Map<Token, Integer> authTokens = Collections.synchronizedMap(new IdentityHashMap<>(2));

        private PackageContext(final Token mainToken) {
            this.mainToken = mainToken;
        }
    }
}
