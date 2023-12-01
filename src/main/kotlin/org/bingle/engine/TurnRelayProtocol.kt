package org.bingle.engine

import org.bingle.dtls.NetworkSourceKey
import org.bingle.interfaces.IDatagramSocket
import org.bingle.util.logDebug
import org.bingle.util.logError
import org.bingle.util.logWarn
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.or

typealias IdListenerMap = MutableMap<String, TurnRelayProtocol.Listener>
typealias AddressChannelMap = MutableMap<InetSocketAddress, Int>

class TurnRelayProtocol(var socket: IDatagramSocket) {
    data class RelayChannel(val senderAddress: InetSocketAddress, val targetAddress: InetSocketAddress, var lastActiveTime: Date?)

    data class Listener(val targetAddress: InetSocketAddress, var lastActiveTime: Date)

    private val listeners: IdListenerMap = mutableMapOf()
    val relayedChannels: MutableList<RelayChannel?> = mutableListOf() // indexed by channel number - 1
    private val accessedChannels: AddressChannelMap = mutableMapOf()

    // Handle a packet that has been identified as TURN, so will be channel data to relay
    // Returns a packet if we have one to process, else null
    fun channelData(packet: DatagramPacket): Pair<ByteArray, NetworkSourceKey>? {
        val channelNumber = channelNumber(packet.data) ?: return null

        if(isRelayedPacket(packet.data)) {
            // Indicates this data is for us relayed bit set
            logDebug("TurnRelayProtocol::channelData ${packet.length} bytes from ${packet.socketAddress} channelNumber=${channelNumber} for us")

            return Pair(
                relayedData(packet.data),
                NetworkSourceKey(packet.socketAddress as InetSocketAddress, channelNumber)
            )
        }

        logDebug("TurnRelayProtocol::channelData ${packet.length} bytes from ${packet.socketAddress} channelNumber=${channelNumber}")

        val channel = relayedChannels.getOrNull(channelNumber-1)
        if(channel == null) {
            logWarn("TurnRelayProtocol::channelData on ${socket?.localSocketAddress} - unknown/closed channel ${channelNumber}, relayedChannels=${relayedChannels}")
            return null
        }

        if(packet.socketAddress != channel.senderAddress && packet.socketAddress != channel.targetAddress) {
            logError("TurnRelayProtocol::channelData - channel ${channel} not valid for ${packet.socketAddress}")
            return null
        }

        channel.lastActiveTime = Date()
        val toAddress = if(packet.socketAddress == channel.senderAddress) channel.targetAddress else channel.senderAddress

        if (socket == null) {
            logError("TurnRelayProtocol::channelData - no socket to send on")
            return null
        }

        logDebug("TurnRelayProtocol::channelData forwarding to ${toAddress} channelNumber=${channelNumber} using ${socket?.localSocketAddress}")

        // Set the bit to indicate this is a relayed TURN packet now
        val relayedData = packet.data.clone()
        relayedData[0] = relayedData[0] or 0x20.toByte()

        socket!!.send(
            DatagramPacket(
                relayedData,
                packet.length,
                toAddress
            )
        )
        return null
    }

//    fun userChannelData(packet: DatagramPacket): Pair<ByteArray, NetworkSourceKey>? {
//        val channelNumber = channelNumber(packet.data) ?: return null
//        logDebug("TurnRelayProtocol::userChannelData ${packet.length} bytes from ${packet.socketAddress} channelNumber=${channelNumber}")
//        //TODO: ignore unopen channels
//        if(channelNumber==0) {
//            logDebug("TurnRelayProtocol::userChannelData Got channel 0, ping response")
//            return null
//        }
//
//        // Packet is for us from a relay
//        return Pair(
//            relayedData(packet.data),
//            NetworkSourceKey(packet.socketAddress as InetSocketAddress, channelNumber)
//        )
//    }

//    fun relayChannelData(packet: DatagramPacket): Boolean {
//        val channelNumber = channelNumber(packet.data) ?: return false
//        logDebug("TurnRelayProtocol::relayChannelData ${packet.length} bytes from ${packet.socketAddress} channelNumber=${channelNumber}")
//
//        if(channelNumber==0) {
//            // Indicates a ping
//            logDebug("TurnRelayProtocol::relayChannelData ping response using ${socket?.localSocketAddress}")
//
//            socket!!.send(
//                DatagramPacket(
//                    packet.data,
//                    packet.length,
//                    packet.socketAddress
//                )
//            )
//
//            return true
//        }
//
//        val channel = relayedChannels.getOrNull(channelNumber-1)
//        if(channel == null) {
//            logWarn("TurnRelayProtocol::relayReceived - on ${socket?.localSocketAddress} unknown/closed channel ${channelNumber}")
//            return false
//        }
//
//        if(packet.socketAddress != channel.senderAddress && packet.socketAddress != channel.targetAddress) {
//            logError("TurnRelayProtocol::relayReceived - channel ${channel} not valid for ${packet.socketAddress}")
//            return false
//        }
//
//        channel.lastActiveTime = Date()
//        val toAddress = if(packet.socketAddress == channel.senderAddress) channel.targetAddress else channel.senderAddress
//
//        if (socket == null) {
//            logError("TurnRelayProtocol::relayReceived - no socket to send on")
//            return false
//        }
//
//        logDebug("TurnRelayProtocol::relayChannelData forwarding to ${toAddress} channelNumber=${channelNumber} using ${socket?.localSocketAddress}")
//
//        socket!!.send(
//            DatagramPacket(
//                packet.data,
//                packet.length,
//                toAddress
//            )
//        )
//        return true
//    }

    fun sendTo(data: ByteArray, to: NetworkSourceKey) {
        sendToSocket(socket!!, data, to)
    }

    fun relayListen(targetAddress: InetSocketAddress, id: String) {
        logDebug("TurnRelayProtocol::listen ${targetAddress} ${id}")
        listeners[id] = Listener(targetAddress, Date())
    }

    fun relayCall(calledId: String, senderAddress: InetSocketAddress): Int? {
        listeners[calledId]?.let { listener ->
            val newRelayChannel = RelayChannel(senderAddress, listener.targetAddress, Date())
            var freeChannelIndex = relayedChannels.indexOf(null)
            if(freeChannelIndex < 0) {
                freeChannelIndex = relayedChannels.size
                relayedChannels.add(newRelayChannel)
            }
            else {
                relayedChannels[freeChannelIndex] = newRelayChannel
            }
            logDebug("TurnRelayProtocol::relayCall on ${socket?.localSocketAddress} allocated ${freeChannelIndex + 1} for ${calledId}, listener = ${listener}")
            return freeChannelIndex + 1
        }

        logWarn("TurnRelayProtocol::relayCall ${calledId} is not a listener")
        return null
    }

    fun addAccessed(channel: Int, relayAddress: InetSocketAddress) {
        accessedChannels[relayAddress] = channel
    }

    fun removeAccessed(relayAddress: InetSocketAddress) {
        accessedChannels.remove(relayAddress)
    }

    val accessedChannelCount: Int get() = accessedChannels.size

    fun keepAlive(id: String) {
        listeners[id]?.lastActiveTime = Date()
    }

    fun purge(beforeDate: Date = Date(Date().time - 300000L)) {
        listeners.filter { it.value.lastActiveTime.before(beforeDate) }.forEach { listeners.remove(it.key) }
        relayedChannels.forEachIndexed {
                index, c -> if(c?.lastActiveTime?.before(beforeDate) == true) relayedChannels[index] = null
        }
    }

    val listenerCount: Int get() = listeners.size

    val relayedChannelCount: Int get() = relayedChannels.filterNotNull().size

    companion object {
        fun sendToSocket(socket: IDatagramSocket, data: ByteArray, to: NetworkSourceKey) {
            if(to.relayChannel == null || to.relayAddress == null) {
                logDebug("TurnRelayProtocol::sendTo = ${to} is not a relay address")
            }
            val packet = ByteArray(4 + data.size)
            val offsetRelayChannel = to.relayChannel!!.toUInt() + 0x4000u
            packet[0] = (offsetRelayChannel shr 8).toByte()
            packet[1] = (offsetRelayChannel and 255u).toByte()
            packet[2] = (data.size shr 8).toByte()
            packet[3] = (data.size and 0xFF).toByte()
            data.copyInto(packet, 4)

            socket.send(
                DatagramPacket(
                    packet,
                    4 + data.size,
                    to.relayAddress
                )
            )
        }
    }

    private fun relayedData(data: ByteArray): ByteArray {
        val applicationDataLen = ((data[2].toUInt() shl 8) + (data[3].toUInt() and 255u)).toInt()
        return data.slice(4 until 4+applicationDataLen).toByteArray()
    }

    private fun isRelayedPacket(data: ByteArray): Boolean =
        (data[0] and 0x20.toByte()) != 0.toByte()


    private fun channelNumber(data: ByteArray): Int? {
        val channelMessage = (data[0].toUInt() shl 8) + (data[1].toUInt() and 255u)
        if(channelMessage < 0x4000u || channelMessage > 0x7FFFu) {
            logDebug("TurnRelayProtocol::channelNumber - offset channel ${channelMessage} not in range")
            return null
        }
        return (channelMessage and 0x1FFFu).toInt()
    }
}