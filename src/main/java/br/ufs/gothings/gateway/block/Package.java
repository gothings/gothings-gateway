package br.ufs.gothings.gateway.block;

import br.ufs.gothings.core.message.GwMessage;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * @author Wagner Macedo
 */
public class Package {
    // Information masks
    public static final int ALL             = 0b11111111;
    public static final int MESSAGE         = 0b00000001;
    public static final int SOURCE_PROTOCOL = 0b00000010;
    public static final int TARGET_PROTOCOL = 0b00000100;
    public static final int REPLY_TO        = 0b00001000;

    // Package information
    private GwMessage message;
    private String sourceProtocol;
    private String targetProtocol;
    private Map<String, Long[]> replyTo;

    private final PackageContext ctx;
    private final PackageInfo fullAccessInfo;
    private final PackageInfo readOnlyInfo;

    private final AtomicInteger passes = new AtomicInteger(0);

    private Package(final PackageContext ctx) {
        this.ctx = ctx;
        this.fullAccessInfo = new PackageInfo(this, ctx.mainToken);
        this.readOnlyInfo = new PackageInfo(this, null);
    }

    public PackageInfo getInfo() {
        return getInfo(null);
    }

    public PackageInfo getInfo(final Token authToken) {
        if (authToken == ctx.mainToken) {
            return fullAccessInfo;
        }
        if (authToken == null) {
            return readOnlyInfo;
        }
        return new PackageInfo(this, authToken);
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

    public static final class PackageInfo {
        private final Package pkg;
        private final int authMask;

        private PackageInfo(Package pkg, Token authToken) {
            this.pkg = pkg;
            if (authToken == pkg.ctx.mainToken) {
                this.authMask = ALL;
            } else {
                this.authMask = (authToken == null) ? 0 : pkg.ctx.authTokens.getOrDefault(authToken, 0);
            }
        }

        public GwMessage getMessage() {
            return pkg.message;
        }

        public void setMessage(final GwMessage message) {
            checkAuth(MESSAGE);
            pkg.message = message;
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

        public Map<String, Long[]> getReplyTo() {
            return pkg.replyTo;
        }

        public void setReplyTo(final Map<String, Long[]> replyTo) {
            checkAuth(REPLY_TO);
            pkg.replyTo = replyTo;
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
        }

        public Package newPackage() {
            return new Package(ctx);
        }

        public void addToken(final Token authToken, final int... infoMasks) {
            final int authMask = IntStream.of(infoMasks).reduce(0, (a, b) -> a | b);
            ctx.authTokens.put(authToken, authMask);
        }
    }

    private static class PackageContext {
        private final Token mainToken;
        private final Map<Token, Integer> authTokens = new IdentityHashMap<>(2);

        private PackageContext(final Token mainToken) {
            this.mainToken = mainToken;
        }
    }
}
