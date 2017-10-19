package io.github.mike10004.nanochamp.server;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import org.apache.http.HttpHeaders;
import org.junit.Test;

import java.util.stream.Stream;

import static org.junit.Assert.*;

public class NanoControlTest {

    @Test
    public void isAnyContentEncodingSpecified() throws Exception {
        assertEquals(false, evaluate(buildResponse()));
        assertEquals(false, evaluate(buildResponse("")));
        assertEquals(false, evaluate(buildResponse("", "")));
        assertEquals(false, evaluate(buildResponse("identity")));
        assertEquals(false, evaluate(buildResponse("identity, identity")));
        assertEquals(false, evaluate(buildResponse("identity, identity", "identity")));
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

}