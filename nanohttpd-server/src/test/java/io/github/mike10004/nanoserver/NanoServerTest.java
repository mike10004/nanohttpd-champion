package io.github.mike10004.nanoserver;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class NanoServerTest {

    @Test
    public void basic() throws Exception {

        NanoServer server = NanoServer.builder()
                .getPath("/hello", NanoResponse.status(200).plainTextUtf8("hello"))
                .build();
        try (NanoControl control = server.startServer();
             CloseableHttpClient client = HttpClients.createSystem()) {
            try (CloseableHttpResponse response = client.execute(new HttpGet(control.buildUri().setPath("/hello").build()))) {
                assertEquals("status", 200, response.getStatusLine().getStatusCode());
                assertEquals("message", "hello", EntityUtils.toString(response.getEntity()));
            }
            try (CloseableHttpResponse response = client.execute(new HttpGet(control.buildUri().setPath("/notfound").build()))) {
                assertEquals("status", 404, response.getStatusLine().getStatusCode());
            }
        }
    }
}