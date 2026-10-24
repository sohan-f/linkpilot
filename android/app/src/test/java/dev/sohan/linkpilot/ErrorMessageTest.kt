package dev.sohan.linkpilot

import dev.sohan.linkpilot.data.describeUnreachable
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessageTest {

    private val host = "192.168.0.1"

    @Test
    fun timeoutDropsOkHttpSourcePortsAndDeadline() {
        val raw = SocketTimeoutException(
            "failed to connect to /192.168.0.1 (port 80) from /10.192.80.252 (port 34280) after 4000ms",
        )

        assertEquals(
            "The router at 192.168.0.1 did not answer in time. Check Wi-Fi and try again.",
            describeUnreachable(raw, host),
        )
    }

    @Test
    fun refusedConnectionNamesTheAddressToCheck() {
        assertEquals(
            "Could not connect to the router at 192.168.0.1. Check Wi-Fi and the router address.",
            describeUnreachable(ConnectException("Failed to connect to /192.168.0.1:80"), host),
        )
    }

    @Test
    fun unknownHostPointsAtTheAddress() {
        assertEquals(
            "Could not resolve 192.168.0.1. Check the router address.",
            describeUnreachable(UnknownHostException("router.lan"), host),
        )
    }

    @Test
    fun otherTransportFailuresKeepTheSameActionableShape() {
        assertEquals(
            "Could not reach the router at 192.168.0.1. Check Wi-Fi and the router address.",
            describeUnreachable(IOException("unexpected end of stream"), host),
        )
    }
}
