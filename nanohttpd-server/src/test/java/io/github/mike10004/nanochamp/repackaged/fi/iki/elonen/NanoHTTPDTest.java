package io.github.mike10004.nanochamp.repackaged.fi.iki.elonen;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This is a test that simulates the race condition where your HTTP client is operating
 * on a separate thread and you want to shut down the NanoHTTPD server after the client
 * fully consumes a response, but you don't get notified that the client has finished
 * consuming a response. This situation happens if you are using Selenium to browse
 * a web site served by NanoHTTPD.
 */
public class NanoHTTPDTest {

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

    @Test
    public void serve() throws Exception {
        int port = findUnusedPort();
        int dataLength = 8192000;
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
            try (InputStream in = url.openStream()) {
                clientReadBegunLatch.countDown();
                ByteStreams.copy(in, bytesRead);
            } catch (IOException e) {
                System.out.println("exception during client read: " + e);
                e.printStackTrace(System.out);
                exceptions.add(e);
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
        int available = dataInput.available();
        System.out.format("data available: %d (of %d)%n", available, dataLength);
        checkState(available > 0, "some data should still be available");
        assertEquals("exceptions", ImmutableList.of(), exceptions);
    }

    private void flush(NanoHTTPD server) {

    }

    private static int findUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

}