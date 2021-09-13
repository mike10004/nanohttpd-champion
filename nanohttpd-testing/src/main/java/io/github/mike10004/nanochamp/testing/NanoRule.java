package io.github.mike10004.nanochamp.testing;

import io.github.mike10004.nanochamp.server.HostAddress;
import io.github.mike10004.nanochamp.server.NanoControl;
import io.github.mike10004.nanochamp.server.NanoServer;
import io.github.mike10004.nanochamp.server.NanoServer.RequestHandler;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;


public class NanoRule extends ExternalResource {

    private final NanoServer server;
    private NanoControl control;

    public static NanoRule withHandlers(RequestHandler firstHandler, RequestHandler secondHandler, RequestHandler...requestHandlers) {
        return new NanoRule(Stream.concat(Stream.of(firstHandler, secondHandler), Arrays.stream(requestHandlers)).collect(Collectors.toList()));
    }

    public static NanoRule withHandler(RequestHandler requestHandler) {
        return new NanoRule(Collections.singleton(requestHandler));
    }

    public NanoRule(NanoServer nanoServer) {
        this.server = requireNonNull(nanoServer);
    }

    public NanoRule(Collection<? extends RequestHandler> requestHandlers) {
        this(NanoServer.builder().handleAll(requestHandlers).build());
    }

    public NanoControl getControl() {
        return control;
    }

    public HostAddress getSocketAddress() {
        if (control == null) {
            throw new IllegalStateException("server not started yet");
        };
        return control.getSocketAddress();
    }

    @Override
    protected synchronized void before() throws Throwable {
        control = server.startServer();
    }

    @Override
    protected synchronized void after() {
        if (control != null) {
            try {
                control.close();
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }
}