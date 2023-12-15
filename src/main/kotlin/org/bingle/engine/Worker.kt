package org.bingle.engine

import com.creatotronik.stun.StunResponse
import com.creatotronik.stun.StunResponseKind
import org.bingle.blockchain.AlgoNetworkException
import org.bingle.certs.BingleCertCreator
import org.bingle.certs.CertChecker
import org.bingle.command.BaseCommand
import org.bingle.dtls.DTLSParameters
import org.bingle.dtls.JavaResourceUtil
import org.bingle.dtls.NetworkSourceKey
import org.bingle.integration.StunServers
import org.bingle.interfaces.*
import org.bingle.util.logDebug
import org.bingle.util.logError
import org.bingle.util.logWarn
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class Worker internal constructor(private val engine: IEngineState) {
    private lateinit var initCommsThread: Thread

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
                                engine.id
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

                    engine.config.onUsername?.invoke(engine.id, engine.myUsername)
                    engine.config.onState?.invoke(CommsState.AUTHORIZING, null, RegisterAction.GOT_USER, null)

                    // If forceRelay is set, we don't need to test NAT connectivity
                    // before becoming a relay
                    if (engine.config.isRelay == true && engine.config.forceRelay == true) {
                        // so advertise us as a relay
                        engine.chainAccess.setRelayState(2) // full relay
                    } else if (engine.config.isRelay == false) {
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

                engine.relay.adoptRelayForPublicEndpoint()
            }

            // TODO: move to own class
            logDebug("Worker::initComms setup stun handler")

            // The stun thread runs any state changes from an IP change
            // and deals with relay initialization including DDB
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
                val certCreator = BingleCertCreator(engine.chainAccess, engine.id)

                logDebug("Worker::initComms init DTLSParameters")
                val dtlsParameters = DTLSParameters(
                    port = engine.config.port ?: 0,  // any port unless we are doing local tests
                    handshakeTimeoutMillis = engine.config.timeouts.handshake,
                    dtlsPacketReceiveTimeout = engine.config.timeouts.dtlsPacketReceive,
                    verifyTimeout = engine.config.timeouts.verify,
                    onMessage = { fromAddress: NetworkSourceKey, verifiedId: String, messageBuffer: ByteArray, _: Int ->
                        logDebug(
                            "Worker::initComms ${engine.config.port} - in listener: ${verifiedId} at ${fromAddress} sent <${
                                String(
                                    messageBuffer
                                )
                            }>"
                        )
                        try {
                            val decodedCommand = BaseCommand.fromJson(String(messageBuffer))

                            fromAddress.inetSocketAddress?.let { decodedCommand.senderAddress = it }
                            decodedCommand.verifiedId = verifiedId

                            logDebug("Worker::initComms ${engine.config.port} - in listener: decodedCommand=${decodedCommand}")
                            if (decodedCommand.hasTag()) {
                                val tag = decodedCommand.tag
                                engine.responseSlots[tag]?.let {
                                    it.msg = decodedCommand
                                    it.latch.countDown()
                                } ?: logDebug("tag ${tag} not found")
                            } else {
                                this.engine.commandRouter.routeCommand(decodedCommand)
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
                    stunServers = if (engine.config.publicEndpoint == null) StunServers.known() else null,
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

                engine.resolver = engine.config.makeResolver(engine)

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
        logDebug("Worker::stop stopping threads")
        initCommsThread.join()
        logDebug("Worker::stop initCommsThread is done")

        engine.stunHandlerDone = true
        engine.stunHandlerQueue.add(StunResponse(StunResponseKind.STOP))

        engine.config.dtlsConnect.clearAll()
        engine.config.dtlsConnect.waitForStopped()
        logDebug("Worker::stop dtlsConnect stopped")

        engine.stunResponseThread.join()
        logDebug("Worker::stop done")
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

            if (endpoint == null || (engine.state == CommsState.ADVERTISED && (endpoint == engine.currentEndpoint )) || engine.state == CommsState.RELAY_ADVERTISED) {
                // logDebug("Worker::handleStunResponse - no sig change")
                onState?.invoke(engine.state, resolveLevel, null, null)
                return
            }

            logDebug("Worker::handleStunResponse - change, need processing endpoint=${endpoint} currentEndpoint=??? state=${engine.state}")

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
            engine.relay.adoptRelayState(endpoint, resolveLevel)
        } catch (ex: Exception) {
            System.err.println("Worker::handleStunResponse threw $ex in handler, response poss not processed")
            ex.printStackTrace(System.err)
        }
    }



}
