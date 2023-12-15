package org.bingle.engine

import com.creatotronik.stun.StunResponse
import com.creatotronik.stun.StunResponseKind
import org.bingle.interfaces.CommsState
import org.bingle.interfaces.NatType
import org.bingle.interfaces.RegisterAction
import org.bingle.interfaces.ResolveLevel
import org.bingle.util.logDebug
import java.util.concurrent.TimeUnit

class StunProcessor(val engine: IEngineState) {

     fun runResponseThread() {
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
                    handleStunResponse(stunResponse)
                    // logDebug("Comms: done with ${stunResponse}")
                }
            }
        }
        engine.stunResponseThread.start()
    }
    
    private fun handleStunResponse(stunResponse: StunResponse?) {
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
                engine.config.onState?.invoke(engine.state, resolveLevel, null, null)
                return
            }

            logDebug("Worker::handleStunResponse - change, need processing endpoint=${endpoint} currentEndpoint=??? state=${engine.state}")

            if (resolveLevel < ResolveLevel.CONSISTENT) {
                logDebug("Worker::handleStunResponse Resolved at ${resolveLevel} ${endpoint}")
                if (resolveLevel > ResolveLevel.SINGLE) {
                    engine.config.onState?.invoke(
                        engine.state,
                        resolveLevel,
                        RegisterAction.INCONSISTENT_STUN_RESPONSE,
                        NatType.SYMMETRIC
                    )
                } else {
                    engine.config.onState?.invoke(engine.state, resolveLevel, RegisterAction.AWAIT_MULTIPLE_STUN_RESPONSE, null)
                    return
                }
            }

            engine.state = CommsState.BOUND
            engine.config.onState?.invoke(engine.state, resolveLevel, RegisterAction.STUN_RESOLVED, null)

            // val thatComms = this -> not needed?
            engine.relay.adoptRelayState(endpoint, resolveLevel)
        } catch (ex: Exception) {
            System.err.println("Worker::handleStunResponse threw $ex in handler, response poss not processed")
            ex.printStackTrace(System.err)
        }
    }
}