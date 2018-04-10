package io.github.mike10004.nanochamp.server;

import com.google.common.io.ByteStreams;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.nanochamp.server.NanoServer.RequestHandler;
import org.apache.http.HttpHeaders;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class NanoControlTest {

    @SuppressWarnings("SimplifiableJUnitAssertion")
    @Test
    public void isAnyContentEncodingSpecified() throws Exception {
        assertEquals(false, evaluate(buildResponse()));
        assertEquals(false, evaluate(buildResponse("")));
        assertEquals(false, evaluate(buildResponse("", "")));
        assertEquals(true, evaluate(buildResponse("br")));
        assertEquals(true, evaluate(buildResponse("gzip")));
        assertEquals(true, evaluate(buildResponse("gzip", "gzip")));
        assertEquals(true, evaluate(buildResponse("gzip", "gzip, br")));
        assertEquals(true, evaluate(buildResponse("gzip, identity")));
        assertEquals(true, evaluate(buildResponse("gzip,identity")));
        assertEquals(true, evaluate(buildResponse("identity, gzip")));
        assertEquals(true, evaluate(buildResponse("identity", "gzip")));
        assertEquals(true, evaluate(buildResponse("gzip", "br")));
    }

    private static boolean evaluate(Response response) {
        return NanoControl.isAnyContentEncodingSpecified(response);
    }

    private static Response buildResponse(String...contentEncodingValues) {
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
        Stream.of(contentEncodingValues).forEach(value -> response.addHeader(HttpHeaders.CONTENT_ENCODING, value));
        return response;
    }

    @Test
    public void useCustomFactory() throws Exception {
        AtomicInteger stopCalls = new AtomicInteger(0);
        String expectedcontent = "OK";
        NanoControl.HttpdImplFactory factory = new NanoControl.HttpdImplFactory() {
            @Override
            public NanoControl.NanoHttpdImpl construct(NanoControl control, int port, RequestHandler defaultRequestHandler) {
                return control.new NanoHttpdImpl(port, defaultRequestHandler) {
                    @Override
                    public void stop() {
                        stopCalls.incrementAndGet();
                        super.stop();
                    }
                };
            }

            @Override
            public String toString() {
                return "CUSTOM_NANO_HTTPD_IMPL_" + System.identityHashCode(this);
            }
        };
        NanoServer server = NanoServer.builder()
                .httpdFactory(factory)
                .get(session -> NanoResponse.status(200).plainTextUtf8(expectedcontent))
                .build();
        String content;
        try (NanoControl control = server.startServer();
            InputStream responseStream = control.baseUri().toURL().openStream()) {
            content = new String(ByteStreams.toByteArray(responseStream), StandardCharsets.US_ASCII);
        }
        assertEquals(expectedcontent, content);
        assertEquals("num calls", 1, stopCalls.get());
    }
}