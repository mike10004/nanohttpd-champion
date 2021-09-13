package io.github.mike10004.nanochamp.server;

import org.junit.Test;

import static org.junit.Assert.*;

public class HostAddressTest {

    @Test
    public void testToString() {

        HostAddress address = new HostAddress("localhost", 51333);
        assertEquals("localhost:51333", address.toString());
    }
}