package br.ufs.gothings.core.util;

import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Wagner Macedo
 */
public final class MapUtils {
    private MapUtils() {
    }

    public static <K, V> K getKey(Map<K,V> map, V value) {
        for (final Entry<K, V> kv : map.entrySet()) {
            if (kv.getValue().equals(value)) {
                return kv.getKey();
            }
        }
        return null;
    }
}
