package io.github.mike10004.nanochamp.repackaged.fi.iki.elonen;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Response.IStatus;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        NanoHTTPD nano = new SingleResponseNanoHTTPD(port, newFixedLengthResponseFactory(NanoHTTPD.Response.Status.OK, "text/plain", dataBytes));
        nano.start();
        URL url = new URL("http://localhost:" + port + "/");
        String responseContent;
        responseContent = new String(readFully(url), StandardCharsets.US_ASCII);
        nano.stop();
        assertEquals("content", dataStr, responseContent);
    }

    private static byte[] readFully(URL url) throws IOException {
        System.out.format("fetching from %s%n", url);
        try (InputStream in = url.openStream()) {
            return ByteStreams.toByteArray(in);
        }
    }

    private static class SingleResponseNanoHTTPD extends NanoHTTPD {

        private final Function<NanoHTTPD.IHTTPSession, NanoHTTPD.Response> responseFactory;

        private SingleResponseNanoHTTPD(int port, Function<NanoHTTPD.IHTTPSession, NanoHTTPD.Response> responseFactory) {
            super(port);
            this.responseFactory = responseFactory;
        }

        @Override
        public final Response serve(NanoHTTPD.IHTTPSession session) {
            return responseFactory.apply(session);
        }
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
        NanoHTTPD nano = new SingleResponseNanoHTTPD(port, newFixedLengthResponseFactory(NanoHTTPD.Response.Status.OK, "text/plain", dataBytes));
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
        System.out.println("calling nano flush");
        nano.flush();
        System.out.println("calling nano.stop()");
        nano.stop();
        if (clientReadThread.isAlive()) {
            clientReadThread.join();
        }
        byte[] bytesReadArray = bytesRead.toByteArray();
        assertArrayEquals("bytes read equals bytes in response", dataBytes, bytesReadArray);
        assertEquals("exceptions", ImmutableList.of(), exceptions);
    }

    @Test
    public void flushManyTimes() throws Exception {
        int numTimes = 5;
        String txt = "hello, world";
        byte[] bytes = txt.getBytes(StandardCharsets.US_ASCII);
        int port = findUnusedPort();
        URL url = new URL("http://localhost:" + port + "/");
        NanoHTTPD nano = new SingleResponseNanoHTTPD(port, newFixedLengthResponseFactory(NanoHTTPD.Response.Status.OK, "text/plain", bytes));
        nano.start();
        try {
            for (int i = 0; i < numTimes; i++) {
                System.out.format("[%d] flush attempt%n", i + 1);
                byte[] received = readFully(url);
                assertArrayEquals("received", bytes, received);
                System.out.println("flushing");
                nano.flush();
            }
        } finally {
            nano.stop();
        }
    }

    @Test
    public void flushConcurrent() throws Exception {
        int numThreads = 25, numFetchers = 25;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        String txt = "hello, world";
        byte[] bytes = txt.getBytes(StandardCharsets.US_ASCII);
        int port = findUnusedPort();
        URL url = new URL("http://localhost:" + port + "/");
        NanoHTTPD nano = new SingleResponseNanoHTTPD(port, newFixedLengthResponseFactory(NanoHTTPD.Response.Status.OK, "text/plain", bytes));
        nano.start();
        List<Callable<byte[]>> fetchers = IntStream.range(0, numFetchers).boxed().map(i -> {
            return new Callable<byte[]>() {
                @Override
                public byte[] call() throws Exception {
                    return readFully(url);
                }
            };
        }).collect(Collectors.toList());
        List<Future<byte[]>> futures;
        try {
            futures = executorService.invokeAll(fetchers);
            nano.flush();
        } finally {
            nano.stop();
        }
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);
        for (Future<byte[]> future: futures) {
            byte[] actual = future.get();
            assertArrayEquals("received bytes", bytes, actual);
        }
    }

    private static int findUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static Function<NanoHTTPD.IHTTPSession, NanoHTTPD.Response> newFixedLengthResponseFactory(IStatus status, String mimeType, byte[] bytes) {
        return session -> NanoHTTPD.newFixedLengthResponse(status, mimeType, new ByteArrayInputStream(bytes), bytes.length);
    }
}