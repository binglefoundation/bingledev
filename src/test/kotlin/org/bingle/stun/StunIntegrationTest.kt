package org.bingle.stun

import org.assertj.core.api.Assertions.assertThat
import org.bingle.integration.StunServers
import org.bingle.util.logDebug
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch

class StunIntegrationTest {
    // REQUIRES: UDP access with at least Restricted Cone NAT
    // At least 4 of the listed stun servers to be awake
    @Test
    fun `can request and process STUN response from known servers`() {
        val stunProtocol = StunProtocol(StunServers.known())

        val datagramSocket = DatagramSocket(1234)

        val locatedEndpoints: MutableSet<String> = mutableSetOf()
        val seenEnoughLatch = CountDownLatch(4)
        var finishedWithThis = false
        val listenThread = Thread {
            while(!finishedWithThis) {
                val buffer = ByteArray(128)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    datagramSocket.receive(packet)
                }
                catch(_: SocketException) {
                    break
                }
                logDebug("Got packet from ${packet.address} length ${packet.length}")

                if (stunProtocol.isStunMessage(packet.data)) {
                    logDebug(" packet identified as Stun")

                    val stunResponse = stunProtocol.decodeStunResponse(packet.data)
                        ?: throw RuntimeException("Could not decode")
                    logDebug(" decodes to ${stunResponse}")
                    locatedEndpoints.add(stunResponse.myAddress.toString())
                    seenEnoughLatch.countDown()
                } else {
                    logDebug(" not stun")
                }
            }
        }

        listenThread.start()

        stunProtocol.initSender(datagramSocket)

        seenEnoughLatch.await()

        // NOTE: this will only work if we are Full Cone or Restricted Cone NAT
        // such that each message opens a port and comes back with a consistent
        // endpoint
        // Symmetric NAT will come back with different endpoints
        // Some ISPs might filter inbound UDP entirely
        assertThat(locatedEndpoints).hasSize(1)

        stunProtocol.stopSender()
        finishedWithThis = true
        try {
            datagramSocket.close()
        }
        catch(_: SocketException) {}
        listenThread.join()
    }
}