package io.github.mike10004.nanochamp.server;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.server.NanoServer.RequestHandler;
import org.apache.http.client.utils.URIBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;

public class NanoControl implements Closeable {

    private final NanoHTTPD server;
    private final ImmutableList<? extends RequestHandler> requestHandlers;
    private final AtomicLong numRequestsMatched = new AtomicLong(0L);
    private final AtomicLong numRequestsHeard = new AtomicLong(0L);

    NanoControl(Iterable<? extends RequestHandler> requestHandlers, RequestHandler defaultRequestHandler) throws IOException {
        this.requestHandlers = ImmutableList.copyOf(requestHandlers);
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        checkState( port > 0 && port < 65536, "port %s", port);
        server = new NanoHTTPD(port) {
            @Override
            public Response serve(IHTTPSession session) {
                numRequestsHeard.incrementAndGet();
                for (RequestHandler handler : NanoControl.this.requestHandlers) {
                    Response response = handler.serve(session);
                    if (response != null) {
                        numRequestsMatched.incrementAndGet();
                        return response;
                    }
                }
                return defaultRequestHandler.serve(session);
            }

            @Override
            protected boolean useGzipWhenAccepted(Response r) {
                return super.useGzipWhenAccepted(r) && !isAnyContentEncodingSpecified(r);
            }
        };
        server.start();

    }

    private static final Splitter contentEncodingValueSplitter = Splitter.on(Pattern.compile(",\\s*")).trimResults().omitEmptyStrings();
    private static final Predicate<String> notIdentity = token -> !"identity".equals(token);

    static boolean isAnyContentEncodingSpecified(NanoHTTPD.Response response) {
        String contentEncoding = response.getHeader(HttpHeaders.CONTENT_ENCODING);
        //noinspection SimplifiableIfStatement
        if (contentEncoding != null) {
            List<String> tokens = contentEncodingValueSplitter.splitToList(contentEncoding);
            return tokens.stream().anyMatch(notIdentity);
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        if (server.wasStarted()) {
            server.stop();
        }
    }

    public int getListeningPort() {
        return server.getListeningPort();
    }

    public HostAndPort getSocketAddress() {
        checkState(server != null, "server not instantiated yet");
        return HostAndPort.fromParts("localhost", getListeningPort());
    }

    public URIBuilder buildUri() {
        try {
            return new URIBuilder("http://" + getSocketAddress() + "/");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public long getNumRequestsHeard() {
        return numRequestsHeard.get();
    }

    public long getNumRequestsMatched() {
        return numRequestsMatched.get();
    }

}
