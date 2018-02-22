package io.github.mike10004.nanochamp.server;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;

public class Tests {

    private Tests() {}

    public static final String SYSPROP_RESERVED_PORT = "nanochamp.unittests.reservedPort";

    private static final Logger log = LoggerFactory.getLogger(Tests.class);

    public static int findPortToUse() throws IOException {
        return findPortToUse(SYSPROP_RESERVED_PORT);
    }

    private static int findPortToUse(String systemPropertyName) throws IOException {
        String portStr = System.getProperty(systemPropertyName);
        if (Strings.isNullOrEmpty(portStr)) { // probably running with IDE test runner, not Maven
            log.trace("unit test port not reserved by build process; will try to find open port");
            try (ServerSocket socket = new ServerSocket(0)) {
                int reservedPort = socket.getLocalPort();
                log.debug("found open port {} by opening socket %s%n", reservedPort, socket);
                return reservedPort;
            }
        } else {
            return Integer.parseInt(portStr);
        }
    }


}
