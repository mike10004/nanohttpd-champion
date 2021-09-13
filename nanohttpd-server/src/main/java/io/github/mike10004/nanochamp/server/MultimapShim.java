package io.github.mike10004.nanochamp.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MultimapShim {

    private MultimapShim() {

    }

    public static <K, V> Map<K, List<V>> create() {
        return new HashMap<K, List<V>>();
    }

    public static <K, V> void put(Map<K, List<V>> map, K key, V value) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public static <V, K> Map<K, List<V>> copyOf(List<Map.Entry<K, V>> list) {
        Map<K, List<V>> m = create();
        list.forEach(entry -> {
            put(m, entry.getKey(), entry.getValue());
        });
        return m;
    }
}
