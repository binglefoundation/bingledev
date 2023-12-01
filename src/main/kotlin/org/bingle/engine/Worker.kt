package org.bingle.engine

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.creatotronik.stun.StunResponse
import com.creatotronik.stun.StunResponseKind
import org.bingle.going.apps.ddb.DistributedDBApp
import org.bingle.going.apps.ddb.RelayPlan
import org.bingle.blockchain.AlgoNetworkException
import org.bingle.certs.BingleCertCreator
import org.bingle.certs.CertChecker
import org.bingle.dtls.DTLSParameters
import org.bingle.dtls.JavaResourceUtil
import org.bingle.dtls.NetworkSourceKey
import org.bingle.interfaces.*
import org.bingle.util.logDebug
import org.bingle.util.logError
import org.bingle.util.logWarn
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class Worker {
    private val engine: IEngineState
    private lateinit var initCommsThread: Thread

    internal constructor(engine: IEngineState) {
        this.engine = engine
    }

    // TODO: move to own class / make configurable
    private val stunServers = listOf(
        "stun4.l.google.com:19302",
        "stun.gmx.net:3478",
        "stun.freeswitch.org:3478",
        "stun.skydrone.aero:3478",
        "stun.voipwise.com:3478",
        "stun.voip.blackberry.com:3478"
    )

    private fun failNoId() {

    }

    fun start() {
        if (null == engine.keyProvider.getId()?.let {
                engine.id = it
            }) {
            logWarn("No id, need signup")
            engine.config.onState?.invoke(CommsState.FAILED, null, RegisterAction.NO_ID, null)
            return
        }

        engine.relayFinder = RelayFinder(engine.chainAccess, engine.id, engine)

        initCommsThread = Thread {

            engine.config.onState?.invoke(CommsState.AUTHORIZING, null, RegisterAction.GOT_ID, null)

            do {
                logDebug("Worker:: call waitForAccess")
                engine.networkChangeProvider.waitForAccess {
                    if (it) engine.config.onState?.invoke(
                        CommsState.AUTHORIZING,
                        null,
                        RegisterAction.NETWORK_AVAILABLE,
                        null
                    )
                    else engine.config.onState?.invoke(
                        CommsState.FAILED, null, RegisterAction.NO_NETWORK,
                        NatType.NO_NETWORK
                    )
                }
                logDebug("Comms: waitForAccess completes")

                var algoOk = false
                try {
                    val balance = engine.chainAccess.assetBalance()
                    if ((balance ?: 0.0) < 1.0) {
                        println("Asset setup for ${engine.id} not done, 1BINGLE not held, need signup")
                        engine.config.onState?.invoke(
                            CommsState.FAILED,
                            null,
                            RegisterAction.ALGO_HOLDING_FAIL,
                            null
                        )
                        return@Thread // -- enter register process
                    }
                    engine.config.onState?.invoke(CommsState.AUTHORIZING, null, RegisterAction.GOT_ASSET, null)


                    if (null == engine.chainAccess.retrying<String?, IChainAccess> {
                            engine.chainAccess.findUsernameByAddress(
                                engine.id!!
                            )
                        }?.let {
                            engine.myUsername = it
                        }) {
                        engine.config.onState?.invoke(
                            CommsState.FAILED,
                            null,
                            RegisterAction.ALGO_HOLDING_FAIL,
                            null
                        )
                        logError("Asset setup for ${engine.id} not done, no swap tag")
                        return@Thread
                    }

                    engine.config.onUsername?.invoke(engine.id!!, engine.myUsername!!)
                    engine.config.onState?.invoke(CommsState.AUTHORIZING, null, RegisterAction.GOT_USER, null)

                    // If forceRelay is set, we don't need to test NAT connectivity
                    // before becoming a relay
                    if (engine.config.relay == true && engine.config.forceRelay == true) {
                        // so advertise us as a relay
                        engine.chainAccess.setRelayState(2) // full relay
                    } else if (engine.config.relay == false) {
                        // we are not a relay, so advertise that
                        engine.chainAccess.setRelayState(0)
                    }
                } catch (ex: AlgoNetworkException) {
                    engine.config.onState?.invoke(
                        CommsState.AUTHORIZING,
                        null,
                        RegisterAction.ALGO_NET_FAIL,
                        null
                    )
                    continue
                } catch (ex: UnknownHostException) {
                    engine.config.onState?.invoke(
                        CommsState.AUTHORIZING,
                        null,
                        RegisterAction.ALGO_NET_FAIL,
                        null
                    )
                    continue
                }

                algoOk = true
            } while (!algoOk)

            if (engine.config.publicEndpoint != null) {
                val publicEndpointIP = InetAddress.getAllByName(engine.config.publicEndpoint)?.firstOrNull()
                    ?: throw RuntimeException("public endpoint configured as ${engine.config.publicEndpoint} but not resolved")

                engine.currentEndpoint = InetSocketAddress(publicEndpointIP, engine.config.port!!)
                logDebug("Worker::InitComms assign current endpoint from engine.config.as ${engine.currentEndpoint}")
                engine.config.onState?.invoke(CommsState.BOUND, null, RegisterAction.USING_PUBLIC, NatType.DIRECT)

                if (engine.config.relay == true && engine.config.forceRelay == true) {
                    logDebug("Worker::InitComms am relay on public endpoint")
                    initDDBApp(engine.currentEndpoint)
                    advertiseAmRelay(engine.currentEndpoint, ResolveLevel.CONSISTENT)
                } else {
                    advertiseResolution(
                        engine.currentEndpoint!!,
                        ResolveLevel.CONSISTENT,
                        NatType.DIRECT
                    )
                }
            }

            // TODO: move to own class
            logDebug("Worker::initComms setup stun handler")

            engine.stunHandlerDone = false
            engine.stunResponseThread = Thread {
                while (!engine.stunHandlerDone) {
                    val stunResponse = engine.stunHandlerQueue.poll(10, TimeUnit.MINUTES)
                    if (stunResponse?.kind == StunResponseKind.STOP) break

                    if (stunResponse != null) {
                        // logDebug("Comms: calling handleStunResponse for ${stunResponse}")
                        handleStunResponse(stunResponse, engine.config.onState)
                        // logDebug("Comms: done with ${stunResponse}")
                    }
                }
            }
            engine.stunResponseThread.start()

            try {
                logDebug("Worker::initComms create certs")
                val certCreator = BingleCertCreator(engine.chainAccess, engine.id!!)

                logDebug("Worker::initComms init DTLSParameters")
                val dtlsParameters = DTLSParameters(
                    port = engine.config.port ?: 0,  // any port unless we are doing local tests
                    handshakeTimeoutMillis = engine.config.timeouts.handshake,
                    dtlsPacketReceiveTimeout = engine.config.timeouts.dtlsPacketReceive,
                    verifyTimeout = engine.config.timeouts.verify,
                    onMessage = { fromAddress: NetworkSourceKey, verifiedId: String?, messageBuffer: ByteArray, _: Int ->
                        logDebug(
                            "Worker::initComms ${engine.config.port} - in listener: ${verifiedId} at ${fromAddress} sent <${
                                String(
                                    messageBuffer
                                )
                            }>"
                        )
                        try {
                            val decodedMessage = Klaxon().parse<MutableMap<String, Any?>>(String(messageBuffer))
                            if (decodedMessage != null) {
                                // TODO: here we decode to a BaseCommand
                                // and call its handler
                                fromAddress.inetSocketAddress?.toString()?.let { decodedMessage["senderAddress"] = it }
                                decodedMessage["verifiedId"] = verifiedId

                                logDebug("Worker::initComms ${engine.config.port} - in listener: decodedMessage=${decodedMessage}")
                                if (decodedMessage["tag"] != null) {
                                    val tag = decodedMessage["tag"]
                                    engine.responseSlots[tag]?.let {
                                        it.msg = decodedMessage
                                        it.latch.countDown()
                                    } ?: logDebug("tag ${tag} not found")
                                } else if (decodedMessage["app"] != null) {
                                    // TODO: get rid of apps
                                    engine.apps[decodedMessage["app"]]?.let {
                                        it.onMessage(verifiedId!!, decodedMessage)
                                        true
                                    } ?: logDebug("${decodedMessage["app"]} not registered in ${engine.apps.keys}")
                                } else {
                                    if (verifiedId != null) {
                                        decodedMessage["senderHandle"] =
                                            engine.chainAccess.findUsernameByAddress(verifiedId)
                                        engine.config.onMessage(decodedMessage)
                                    } else logError("Message ${decodedMessage} has no verifiedId")
                                }
                            }
                        } catch (ex: Exception) {
                            System.err.println("Worker::onMessage threw $ex in handler, message prob not received")
                            ex.printStackTrace(System.err)
                        }
                    },
                    resources = JavaResourceUtil(),
                    onStunResponse = { stunResponse ->
                        // logDebug("Worker::onStunresponse send ${stunResponse}, stunHandlerDone=${stunHandlerDone}")
                        // TODO: moves to own class
                        engine.stunHandlerQueue.put(stunResponse)
                    },
                    stunServers = if (engine.config.publicEndpoint == null) stunServers else null,
                    serverEncryptionCert = certCreator.serverEncryptionCert,
                    serverEncryptionKey = certCreator.serverEncryptionKey,
                    serverSigningCert = certCreator.serverSigningCert,
                    serverSigningKey = certCreator.serverSigningKey,
                    clientCert = certCreator.clientCert,
                    clientKey = certCreator.clientKey,
                    caCert = certCreator.caCert,
                    disableListener = engine.config.disableListener == true,
                    onCertificates = { _: InputStream, presentedCaCert: InputStream ->
                        // TODO: own class
                        // The DTLS layer should respond to any exception here by raising a fatal alert
                        val issuer = CertChecker(presentedCaCert).issuer
                        val id = IdUtils.fromIssuer(issuer).id
                        val username =
                            engine.chainAccess.retrying<String?, IChainAccess> {
                                engine.chainAccess.findUsernameByAddress(
                                    id
                                )
                            }
                                ?: throw RuntimeException("Id ${id} is not registered")
                        logDebug("id ${id}, username ${username} connected")
                        id // verified id, registered and signed
                    },
                )

                if (!engine.config.dtlsConnect.isInitialized) {
                    logDebug("Worker::initComms init DTLS")
                    engine.config.dtlsConnect.init(dtlsParameters)
                } else {
                    engine.config.dtlsConnect.restart()
                }

                engine.networkChangeProvider.register {
                    engine.config.dtlsConnect.clearAll()

                    logDebug("networkChangeProvider ${it}")
                    if (it) {
                        engine.config.onState?.invoke(engine.state, null, RegisterAction.NETWORK_AVAILABLE, null)
                        engine.config.dtlsConnect.stunProtocol.flagNetworkChange()
                    } else engine.config.onState?.invoke(
                        CommsState.FAILED, null, RegisterAction.NO_NETWORK,
                        NatType.NO_NETWORK
                    )
                }

                if (engine.config.requestPingables != null) {
                    engine.pinger = Pinger(engine, engine.config.requestPingables!!, { pingTargetId, availability ->
                        logDebug("Worker::onAvailability $pingTargetId : $availability")
                        engine.config.onAvailability?.invoke(engine.id, availability)
                    })
                    engine.pinger.run()
                }

                engine.listening = true
            } catch (ex: Exception) {
                System.err.println("Worker::initComms threw $ex")
                ex.printStackTrace(System.err)
                engine.config.onState?.invoke(CommsState.FAILED, null, RegisterAction.EXCEPTION, null)
            }
        }

        initCommsThread.start()

    }

    fun stop() {
        logDebug("Comms::stop stopping threads")
        initCommsThread.join()

        engine.stunHandlerDone = true
        engine.stunHandlerQueue.add(StunResponse(StunResponseKind.STOP))

        engine.config.dtlsConnect.clearAll()

        engine.config.dtlsConnect.waitForStopped()
        engine.stunResponseThread.join()
        logDebug("Comms::stop done")
    }

    private fun advertiseAmRelay(
        endpoint: InetSocketAddress,
        resolveLevel: ResolveLevel
    ) {
        logDebug("Worker::advertiseAmRelay ${endpoint}")
        engine.currentEndpoint?.let {
            engine.advertiser.advertiseAmRelay(engine.keyProvider, it, endpoint)
        }

        engine.state = CommsState.RELAY_ADVERTISED
        engine.config.onState?.invoke(engine.state, resolveLevel, RegisterAction.ADVERTISED_IS_RELAY, null)
    }

    private fun advertiseResolution(
        endpoint: InetSocketAddress,
        resolveLevel: ResolveLevel,
        natType: NatType
    ) {
        logDebug("Worker::advertiseResolution ${endpoint}")
        engine.advertiser.advertise(
            engine.keyProvider,
            endpoint
        )
        engine.state = CommsState.ADVERTISED
        engine.config.onState?.invoke(engine.state, resolveLevel, RegisterAction.ADVERTISED_DIRECT, natType)
    }

    // TODO: migrate to not app
    // and use command not map
    private fun initDDBApp(endpoint: InetSocketAddress) {
        val relayToUse = engine.relayFinder!!.find()
        val relayPlan = if (relayToUse == null) {
            val res = RelayPlan()
            res.bootstrap(engine.id)
            res
        } else {
            throw NotImplementedError("TODO: get relay plan from peer")
        }

        engine.distributedDBApp = DistributedDBApp(engine.id, relayPlan) { id: String, command: Map<String, Any> ->
            val sendRes = engine.sender.sendToIdForResponse(id, command.mapValues {
                // TODO: recursive map, or avoid the need
                if (it.value is JsonArray<*>) {
                    (it.value as JsonArray<*>).map { v -> v.toString() }
                } else if (it.value is JsonObject) {
                    (it.value as JsonObject).entries.map { (k, v) -> Pair(k, v.toString()) }.toMap()
                } else {
                    it.value.toString()
                }
            }, null)
            sendRes["fail"] == null
        }
        engine.apps[engine.distributedDBApp.type] = engine.distributedDBApp

        if (engine.config.registerIP) {
            engine.chainAccess.registerIP(engine.id, endpoint)
        }
    }

    //TODO: move to own class
    private fun handleStunResponse(stunResponse: StunResponse?, onState: CommsStateHandler?) {
        if (stunResponse == null) {
            logDebug("Worker::handleStunResponse Got null stun response")
            return
        }

        try {
            engine.stunResolver.addResponse(stunResponse)
            val (resolveLevel, endpoint) = engine.stunResolver.analyse()
            // logDebug("Worker::handleStunResponse Got stun response ${stunResponse}, state=${state} resolveLevel=${resolveLevel}, endpoint=${endpoint}, currentEndpoint=${currentEndpoint}")

            if (endpoint == null || (engine.state == CommsState.ADVERTISED && (endpoint == engine.currentEndpoint || engine.currentEndpoint == null)) || engine.state == CommsState.RELAY_ADVERTISED) {
                // logDebug("Worker::handleStunResponse - no sig change")
                onState?.invoke(engine.state, resolveLevel, null, null)
                return
            }

            logDebug("Worker::handleStunResponse - change, need processing endpoint=${endpoint} currentEndpoint=${engine.currentEndpoint} state=${engine.state}")

            if (resolveLevel < ResolveLevel.CONSISTENT) {
                logDebug("Worker::handleStunResponse Resolved at ${resolveLevel} ${endpoint}")
                if (resolveLevel > ResolveLevel.SINGLE) {
                    onState?.invoke(
                        engine.state,
                        resolveLevel,
                        RegisterAction.INCONSISTENT_STUN_RESPONSE,
                        NatType.SYMMETRIC
                    )
                } else {
                    onState?.invoke(engine.state, resolveLevel, RegisterAction.AWAIT_MULTIPLE_STUN_RESPONSE, null)
                    return
                }
            }

            engine.state = CommsState.BOUND
            onState?.invoke(engine.state, resolveLevel, RegisterAction.STUN_RESOLVED, null)

            // val thatComms = this -> not needed?
            if (engine.config.useRelays == false || engine.config.forceRelay == true) {
                engine.currentEndpoint = endpoint
                onState?.invoke(
                    engine.state,
                    resolveLevel,
                    if (engine.config.useRelays == false) RegisterAction.NOT_USING_RELAYS else RegisterAction.FORCE_BE_RELAY,
                    NatType.UNKNOWN
                )

                advertiseResolution(endpoint, resolveLevel, NatType.DIRECT)
                logDebug("Worker::handleStunResponse (${if (engine.config.useRelays == false) "not using relays" else "force to be relay"}) assign current endpoint from Stun as ${endpoint}")

                if (engine.config.relay == true && engine.config.forceRelay == true) {
                    // Forced relay, normally direct
                    initDDBApp(endpoint)
                    advertiseAmRelay(endpoint, ResolveLevel.CONSISTENT)
                }
            } else if (engine.config.alwaysRelayWithId != null) {
                logDebug("Worker::InitComms (always relay) assign current endpoint from Stun as ${endpoint}")
                engine.currentEndpoint = endpoint

                becomeRelayClient(resolveLevel, true)
            } else if (resolveLevel == ResolveLevel.INCONSISTENT) {
                logDebug("Worker::InitComms (inconsistent Stun responses) assign current endpoint from Stun as ${endpoint}")
                engine.currentEndpoint = endpoint

                becomeRelayClient(resolveLevel, false, NatType.SYMMETRIC)
            } else {
                logDebug("Worker::handleStunResponse - $engine.id finds relay for TriPing")
                val relayForTriPing = engine.relayFinder!!.find()
                logDebug("Worker::handleStunResponse - $engine.id triangleTest relay ${relayForTriPing}")
                if (relayForTriPing != null) {
                    logDebug("Worker::handleStunResponse $engine.id - Sending triangleTest1 request")
                    onState?.invoke(engine.state, resolveLevel, RegisterAction.TRIANGLE_PINGING, null)
                    val trianglePingResponse = engine.sender.sendToIdForResponse(
                        relayForTriPing.first,
                        mapOf(
                            "app" to "relay",
                            "type" to "triangleTest1",
                            "checkingAddress" to endpoint.address.hostAddress,
                            "checkingPort" to endpoint.port.toString(),
                        ),
                        engine.config.timeouts.triPing ?: 60000
                    )

                    if (trianglePingResponse["fail"] == null) {
                        logDebug("Worker::handleStunResponse $engine.id- Got triangleTest response ${trianglePingResponse}")
                        onState?.invoke(
                            engine.state, resolveLevel, RegisterAction.TRIANGLE_PING_RECEIVED,
                            NatType.FULL_CONE
                        )

                        advertiseResolution(endpoint, resolveLevel, NatType.FULL_CONE)
                        if (engine.config.relay == true && engine.config.forceRelay != true) {
                            // relay on a full cone NAT
                            initDDBApp(endpoint)
                            advertiseAmRelay(endpoint, resolveLevel)
                        }
                    } else {
                        logDebug("Worker::handleStunResponse $engine.id - triangleTest fail ${trianglePingResponse}, we need relay")
                        onState?.invoke(
                            engine.state, resolveLevel, RegisterAction.TRIANGLE_PING_FAILED,
                            NatType.RESTRICTED_CONE
                        )

                        sendRelayListen(relayForTriPing.first, relayForTriPing.second)
                        advertiseUsingRelay(relayForTriPing.first, NatType.RESTRICTED_CONE)
                    }
                } else {
                    logError("Worker::handleStunResponse - No relays available")
                    engine.state = CommsState.FAILED
                    onState?.invoke(
                        engine.state, resolveLevel, RegisterAction.NO_RELAYS_AVAILABLE,
                        NatType.UNKNOWN
                    )
                }
            }
        } catch (ex: Exception) {
            System.err.println("Worker::handleStunResponse threw $ex in handler, response poss not processed")
            ex.printStackTrace(System.err)
        }
    }

    // TODO: own class
    private fun becomeRelayClient(
        resolveLevel: ResolveLevel,
        forced: Boolean,
        natType: NatType = NatType.UNKNOWN
    ) {
        logDebug("Worker::becomeRelayClient finds relay")
        val relayToUse = engine.relayFinder.find() // will honour alwaysRelayWithId
        if (relayToUse == null) {
            logError("Comms:onStunResponse - relay ${engine.config.alwaysRelayWithId} is not available")
            engine.state = CommsState.FAILED
            engine.config.onState?.invoke(
                engine.state,
                resolveLevel,
                if (engine.config.alwaysRelayWithId != null) RegisterAction.RELAY_NOT_AVAILABLE else RegisterAction.NO_RELAYS_AVAILABLE,
                natType
            )
        } else {
            if (forced) {
                engine.config.onState?.invoke(
                    engine.state, resolveLevel, RegisterAction.FORCED_USE_RELAY,
                    natType
                )
            }

            if (sendRelayListen(relayToUse.first, relayToUse.second)) {
                advertiseUsingRelay(relayToUse.first, natType)
            } else {
                logError("Comms:onStunResponse - relay ${engine.config.alwaysRelayWithId} did not respond to listen")
                engine.state = CommsState.FAILED
                engine.config.onState?.invoke(
                    engine.state,
                    resolveLevel,
                    if (engine.config.alwaysRelayWithId != null) RegisterAction.RELAY_NOT_AVAILABLE else RegisterAction.NO_RELAYS_AVAILABLE,
                    natType
                )
            }
        }
    }

    private fun sendRelayListen(relayId: String, relayAddress: InetSocketAddress): Boolean {
        // config.dtlsConnect!!.sendRelayPing(relayAddress)

        val res = engine.sender.sendToIdForResponse(
            relayId,
            mapOf("app" to "relay", "type" to "listen"),
            engine.config.timeouts.relayListen,
        )

        return !res.contains("fail")
    }

    private fun advertiseUsingRelay(
        relayId: String,
        natType: NatType
    ) {
        logDebug("Worker::advertiseUsingRelay id ${relayId}")
        engine.advertiser.advertiseUsingRelay(engine.keyProvider, relayId)

        engine.state = CommsState.ADVERTISED
        engine.config.onState?.invoke(
            engine.state,
            ResolveLevel.NONE,
            RegisterAction.ADVERTISED_VIA_RELAY,
            natType
        )
    }

}
