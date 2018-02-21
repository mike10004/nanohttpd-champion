package io.github.mike10004.nanochamp.repackaged.fi.iki.elonen;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * This is a test that simulates the race condition where your HTTP client is operating
 * on a separate thread and you want to shut down the NanoHTTPD server after the client
 * fully consumes a response, but you don't get notified that the client has finished
 * consuming a response. This situation happens if you are using Selenium to browse
 * a web site served by NanoHTTPD.
 */
public class NanoHTTPDTest {

    /*
     * This test confirms that normal usage of the server still works.
     */
    @Test
    public void serveNormally() throws Exception {
        int port = findUnusedPort();
        String dataStr = "hello";
        byte[] dataBytes = dataStr.getBytes(StandardCharsets.US_ASCII);
        int dataLength = dataBytes.length;
        NanoHTTPD.Response response = new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK, "text/plain", new ByteArrayInputStream(dataBytes), dataLength);
        NanoHTTPD nano = new NanoHTTPD(port) {
            @Override
            public Response serve(IHTTPSession session) {
                return response;
            }
        };
        nano.start();
        URL url = new URL("http://localhost:" + port + "/");
        String responseContent;
        try (InputStream in = url.openStream()) {
            responseContent = new String(ByteStreams.toByteArray(in), StandardCharsets.US_ASCII);
        }
        nano.stop();
        assertEquals("content", dataStr, responseContent);
    }

    /*
     * This test would (most likely) fail if the server flush doesn't
     * wait for all responses to finish being sent.
     */
    @Test
    public void serve() throws Exception {
        int port = findUnusedPort();
        int dataLength = 8192000 * 2;
        byte[] dataBytes = new byte[dataLength];
        Random random = new Random(getClass().getName().hashCode());
        random.nextBytes(dataBytes);
        ByteArrayInputStream dataInput = new ByteArrayInputStream(dataBytes);
        NanoHTTPD.Response response = new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK, "text/plain", dataInput, dataLength);
        NanoHTTPD nano = new NanoHTTPD(port) {
            @Override
            public Response serve(IHTTPSession session) {
                return response;
            }
        };
        nano.start();
        List<IOException> exceptions = new ArrayList<>();
        URL url = new URL("http://localhost:" + port + "/");
        ByteArrayOutputStream bytesRead = new ByteArrayOutputStream(dataLength);
        CountDownLatch clientReadBegunLatch = new CountDownLatch(1);
        Thread clientReadThread = new Thread(() -> {
            System.out.println("opening connection to " + url);
            long n = -1;
            try (InputStream in = url.openStream()) {
                clientReadBegunLatch.countDown();
                n = ByteStreams.copy(in, bytesRead);
            } catch (IOException e) {
                System.out.println("exception during client read: " + e);
                exceptions.add(e);
            } finally {
                System.out.format("%d bytes transferred%n", n);
            }

        });
        clientReadThread.start();
        clientReadBegunLatch.await();
        flush(nano);
        System.out.println("calling nano.stop()");
        nano.stop();
        if (clientReadThread.isAlive()) {
            clientReadThread.join();
        }
        byte[] bytesReadArray = bytesRead.toByteArray();
        assertArrayEquals("bytes read equals bytes in response", dataBytes, bytesReadArray);
//        int available = dataInput.available();
//        System.out.format("data available: %d (of %d)%n", available, dataLength);
//        checkState(available > 0, "some data should still be available");
        assertEquals("exceptions", ImmutableList.of(), exceptions);
    }

    private void flush(NanoHTTPD server) throws InterruptedException {
        // System.out.println("calling nano flush");
        server.flush();
    }

    private static int findUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

}