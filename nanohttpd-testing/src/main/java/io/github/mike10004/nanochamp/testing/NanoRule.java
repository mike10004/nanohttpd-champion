package io.github.mike10004.nanochamp.testing;

import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;
import io.github.mike10004.nanoserver.NanoControl;
import io.github.mike10004.nanoserver.NanoServer;
import io.github.mike10004.nanoserver.NanoServer.RequestHandler;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class NanoRule extends ExternalResource {

    private final NanoServer server;
    private NanoControl control;

    public static NanoRule withHandlers(RequestHandler firstHandler, RequestHandler secondHandler, RequestHandler...requestHandlers) {
        return new NanoRule(Lists.asList(firstHandler, secondHandler, requestHandlers));
    }

    public static NanoRule withHandler(RequestHandler requestHandler) {
        return new NanoRule(Collections.singleton(requestHandler));
    }

    public NanoRule(NanoServer nanoServer) {
        this.server = checkNotNull(nanoServer);
    }

    public NanoRule(Collection<? extends RequestHandler> requestHandlers) {
        this(NanoServer.builder().handleAll(requestHandlers).build());
    }

    public NanoControl getControl() {
        return control;
    }

    public HostAndPort getSocketAddress() {
        checkState(control != null, "server not started yet");
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