package br.ufs.gothings.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class PluginSettings {
    private final AtomicBoolean locked;
    private int port;

    public PluginSettings(AtomicBoolean locked) {
        this.locked = locked;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        writeCheck();
        this.port = port;
    }

    private void writeCheck() {
        if (locked.get()) {
            throw new IllegalStateException("Settings locked for writing");
        }
    }
}
