package io.github.mike10004.nanochamp.server;

import javax.annotation.Nullable;

class GuavaShim {

    private GuavaShim() {}

    public static void checkArgument(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void checkState(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public static boolean isNullOrEmpty(@Nullable String contentEncoding) {
        return contentEncoding == null || contentEncoding.isEmpty();
    }
}
