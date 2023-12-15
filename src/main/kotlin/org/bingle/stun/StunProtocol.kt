package org.bingle.stun


import com.creatotronik.stun.StunResponse
import com.creatotronik.stun.StunResponseKind
import de.javawi.jstun.attribute.ChangeRequest
import de.javawi.jstun.attribute.ErrorCode
import de.javawi.jstun.attribute.MappedAddress
import de.javawi.jstun.attribute.MessageAttributeInterface
import de.javawi.jstun.header.MessageHeader
import de.javawi.jstun.header.MessageHeaderInterface
import de.javawi.jstun.header.MessageHeaderParsingException
import org.bingle.util.logDebug
import org.bingle.util.logError
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.*
import java.util.Calendar.SECOND
import kotlin.concurrent.timer


private const val INITIAL_INTERVAL = 15
private const val RETRY_LIMIT = 90
private const val MAGIC_COOKIE = 0x2112A442

class StunProtocol(private val stunServers: List<String>?) {

    private var timer: Timer? = null
    private var hasNetworkChanged = false

    private data class ServerRequestInfo(
        val server: String,
        var interval: Int,
        var nextSend: Date,
        var transactionID: ByteArray
    )

    private val serverRequestsOutstanding = HashMap<String, ServerRequestInfo>()

    fun isStunMessage(data: ByteArray?): Boolean {
        try {
            MessageHeader.parseHeader(data)
        } catch (_: MessageHeaderParsingException) {
            return false
        }

        return true
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun decodeStunResponse(stunResponse: ByteArray): StunResponse? {
        val messageHeader = try {
            MessageHeader.parseHeader(stunResponse)
        } catch (ex: MessageHeaderParsingException) {
            System.err.println("decodeStunResponse: ${ex}")
            return null
        }

        messageHeader.parseAttributes(stunResponse)

        val trID = messageHeader.transactionID
        val request = serverRequestsOutstanding.values.find { it.transactionID.contentEquals(trID) }
        if (request == null) {
            System.err.println("decodeStunResponse: request has unknown transaction id ${trID.toHexString()}")
            return null
        }

        // Got a response, dont re-request for 5 mins
        request.interval = 300
        request.nextSend = timePlusSeconds(request.interval)

        val ma =
            messageHeader.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress?
        // We dont accept XorMappedAddress entries, there seems to be a set of stun servers that dont respond
        // with these
        // No fundamental reason except that JSTUN doesnt support this

       // val messageHeaderKlass = messageHeader.javaClass
     //   val messageHeaderMa = messageHeaderKlass.getDeclaredField("ma")
     //   val treeMap = messageHeaderMa.get(messageHeader) as TreeMap<*, *>

//        val xma = treeMap.get(StunMessageAttributeInterface.MessageAttributeType.XorMappedAddress as Enum<*>)
//            as MappedAddress

        val ec =
            messageHeader.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.ErrorCode) as ErrorCode?
        if (ec != null) {
            logError("decodeStunResponse: request has error ${ec.responseCode}, ${ec.reason}")
            return null
        }

        if (ma != null) {
            return StunResponse(
                StunResponseKind.PLAIN,
                request.server,
                InetSocketAddress(ma.address.inetAddress, ma.port)
            )
        }

//        if (xma != null) {
//            val addressBytes = xma.address.bytes
//            val decodedAddressBytes = when (addressBytes.size) {
//                4 -> addressBytes.xor(MAGIC_COOKIE.toByteArray())
//                16 -> addressBytes.xor(MAGIC_COOKIE.toByteArray() + trID)
//                else -> throw RuntimeException("${addressBytes.size} bytes unexpected asa address size")
//            }
//            val decodedPort = xma.port xor (MAGIC_COOKIE / 65536)
//            return StunResponse(
//                StunResponseKind.XOR,
//                request.server,
//                InetSocketAddress(InetAddress.getByAddress(decodedAddressBytes), decodedPort)
//            )
//        }

        logError("decodeStunResponse: request has null MappedAddress and XorMappedAddress")
        return null
    }

    fun stopSender() {
        timer?.cancel()
    }

    fun initSender(socket: DatagramSocket) {
        if (stunServers == null || stunServers.isEmpty()) return

        requestAllServers(stunServers, socket)

        timer = timer("stunSender", period = 1000) {
            if (hasNetworkChanged) {
                hasNetworkChanged = false
                requestAllServers(stunServers, socket)
            } else {
                serverRequestsOutstanding.entries.forEach {
                    if (it.value.nextSend.before(Date())) {
                        val transactionID = sendRequest(socket, it.key)
                        if (transactionID != null) {
                            var nextInterval = it.value.interval * 2
                            if (nextInterval > RETRY_LIMIT) nextInterval = RETRY_LIMIT
                            val nextSend = timePlusSeconds(nextInterval)

                            logDebug(
                                "sent request, interval=${nextInterval}, nextSend=${nextSend}"
                            )
                            serverRequestsOutstanding[it.key] = ServerRequestInfo(
                                it.key, nextInterval,
                                nextSend,
                                transactionID
                            )
                        }
                    }
                }
            }
        }
    }

    fun flagNetworkChange() {
        hasNetworkChanged = true
    }

    private fun requestAllServers(
        stunServers: List<String>,
        socket: DatagramSocket
    ) {
        logDebug("StunProtocol::requestAllServers ${stunServers}");
        stunServers.forEach {
            val transactionID = sendRequest(socket, it)
            if (transactionID != null) {
                serverRequestsOutstanding[it] = ServerRequestInfo(
                    it, INITIAL_INTERVAL,
                    timePlusSeconds(INITIAL_INTERVAL),
                    transactionID
                )
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun sendRequest(socket: DatagramSocket, serverAndPort: String): ByteArray? {
        val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
        sendMH.generateTransactionID()

        val changeRequest = ChangeRequest()
        sendMH.addMessageAttribute(changeRequest)

        val data = sendMH.bytes
        val (server, port) = serverAndPort.split(":")

        val stunAddress = InetSocketAddress(server, port.toIntOrNull() ?: 3478)
        if (stunAddress.isUnresolved) {
            logError("${server} not found in DNS")
            return null
        }

        val packet = DatagramPacket(data, 0, data.size, stunAddress)
        socket.send(packet)

        logDebug("StunProtocol::sendRequest (${socket.localPort}) -Sent request to server ${serverAndPort}, trID=${sendMH.transactionID.toHexString()}")

        return sendMH.transactionID
    }

    private fun timePlusSeconds(seconds: Int): Date {
        val tim = Calendar.getInstance()
        tim.add(SECOND, seconds)
        return tim.time
    }
}

