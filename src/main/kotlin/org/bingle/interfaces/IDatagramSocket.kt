package org.bingle.interfaces

import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.SocketAddress

interface IDatagramSocket {

    val localSocketAddress: SocketAddress?
    fun send(datagramPacket: DatagramPacket)
}