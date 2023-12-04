package org.bingle.command

import java.net.InetSocketAddress

open class RelayCommand : BaseCommand() {

    data class Call(val calledId: String) : RelayCommand()

    data class RelayResponse(val channel: Int? = null) : RelayCommand()

    data class TriangleTest1(val checkingEndpoint: InetSocketAddress) : RelayCommand()
    data class TriangleTest2(val checkingId: String, val checkingEndpoint: InetSocketAddress) : RelayCommand()
    class TriangleTest3: RelayCommand()

    class Listen : RelayCommand()
    class Check : RelayCommand()
    class ListenResponse : RelayCommand()
    class CheckResponse(val available: Int) : RelayCommand()
    class CallResponse(val calledId: String, val channel: Int) : RelayCommand()
    class KeepAlive() : RelayCommand()
}