package io.github.mike10004.nanochamp.testing;

import io.github.mike10004.nanochamp.server.NanoResponse;
import io.github.mike10004.nanochamp.server.NanoServer;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;

public class NanoRuleTest {

    @Rule
    public NanoRule nanoRule = new NanoRule(NanoServer.builder().getPath("/hello", NanoResponse.status(200).plainTextUtf8("OK")).build());

    @Test
    public void getHello() throws Exception {
        SimpleResponse response = fetch(new URIBuilder(nanoRule.getControl().baseUri()).setPath("/hello").build());
        assertEquals("status", SC_OK, response.status);
        assertEquals("body", "OK", response.body);
    }

    @Test
    public void getNotFound() throws Exception {
        SimpleResponse response = fetch(new URIBuilder(nanoRule.getControl().baseUri()).setPath("/goodbye").build());
        assertEquals("status", SC_NOT_FOUND, response.status);
    }

    private static SimpleResponse fetch(URI uri) throws IOException {
        try (CloseableHttpClient client = HttpClients.createSystem()) {
            try (CloseableHttpResponse response = client.execute(new HttpGet(uri))) {
                return new SimpleResponse(response.getStatusLine().getStatusCode(), EntityUtils.toString(response.getEntity()));
            }
        }
    }

    private static class SimpleResponse {
        public final int status;
        public final String body;

        private SimpleResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}