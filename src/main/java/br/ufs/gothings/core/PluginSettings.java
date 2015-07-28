package br.ufs.gothings.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class PluginSettings {
    private final AtomicBoolean locked;
    private final String[] protocols;
    private int port;

    public PluginSettings(AtomicBoolean locked, String... protocols) {
        this.locked = locked;
        this.protocols = protocols;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        writeCheck();
        this.port = port;
    }

    public String[] getProtocols() {
        return protocols;
    }

    private void writeCheck() {
        if (locked.get()) {
            throw new IllegalStateException("Settings locked for writing");
        }
    }
}
