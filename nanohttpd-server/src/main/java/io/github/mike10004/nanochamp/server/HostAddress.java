package io.github.mike10004.nanochamp.server;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class HostAddress {

    private final String host;
    private final int port;

    public HostAddress(String host, int port) {
        this.host = requireNonNull(host);
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return String.format("%s:%s", host, port);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HostAddress)) return false;
        HostAddress that = (HostAddress) o;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
