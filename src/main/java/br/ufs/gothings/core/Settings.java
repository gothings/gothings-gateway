package br.ufs.gothings.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class Settings {
    private final AtomicBoolean locked;
    private final Map<String, Object> properties = new HashMap<>();

    public Settings(AtomicBoolean locked) {
        this.locked = locked;
    }

    public Object get(String key) {
        return properties.get(key);
    }

    public void set(String key, Object property) {
        writeCheck();
        if (property == null) {
            properties.remove(key);
        } else {
            properties.put(key, property);
        }
    }

    private void writeCheck() {
        if (locked.get()) {
            throw new IllegalStateException("settings locked for writing");
        }
    }
}
