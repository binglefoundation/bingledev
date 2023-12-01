package org.bingle.engine

import org.bingle.interfaces.SendProgress
import org.bingle.util.logDebug
import java.util.*
import kotlin.concurrent.timerTask

class Pinger {

    private val engine: IEngineState
    private val requestPingables: (() -> List<Pingable>)?
    private val onAvailabilityChange: ((id: String, availability: TargetAvailability) -> Unit)?
    private val now: (() -> Date)

    internal constructor(
        engine: IEngineState,
        requestPingables: (() -> List<Pingable>)?,
        onAvailabilityChange: ((id: String, availability: TargetAvailability) -> Unit)?,
        now: (() -> Date) = { Date() }
    ) {
        this.engine = engine
        this.requestPingables = requestPingables
        this.onAvailabilityChange = onAvailabilityChange
        this.now = now
        this.pingTargets = emptyList()
    }

    enum class TargetAvailability {
        UNKNOWN,
        OFFLINE,
        RESOLVED,
        RESPONDS,
        CONNECTS,
    }

    data class PingTarget(
        val pingable: Pingable,
        var delayS: Long = 120,
        var nextPing: Date? = null,
        var currentAvailability: TargetAvailability = TargetAvailability.UNKNOWN,
        var availability: TargetAvailability = TargetAvailability.UNKNOWN
    )

    var pingTargets: List<PingTarget>
    var nextRequest: Date? = null

    fun run() {
        action(false)
    }

    fun action(timer: Boolean) {
        logDebug("Pinger::action from timer: ${timer}, starts, now=${Date()}")
        val nextActionTime = doAction()
        logDebug("Pinger::action done, now=${now()}, nextActionTime=$nextActionTime")
        Timer("Pinger").schedule(timerTask { action(true) }, nextActionTime)
    }

    fun doAction(): Date {
        if (nextRequest == null || nextRequest!!.before(now())) {
            reloadPingables()
            if (nextRequest == null) {
                pingTargets.forEach { it.nextPing = Date(now().time + 10000) }
            }
            nextRequest = Date(now().time + 300000)
        }

        val nextTarget = pingTargets.find { it.nextPing == null || it.nextPing!!.before(now()) }
        if (nextTarget != null) {
            logDebug("Pinger::action Pings target ${nextTarget}")
            val prevAvailability = nextTarget.availability

            checkTarget(nextTarget)
            if (nextTarget.currentAvailability != TargetAvailability.RESPONDS && nextTarget.availability != TargetAvailability.UNKNOWN) {
                logDebug("Pinger::action, ${nextTarget.pingable.id} - next ping due and otherside did not respond, set availability to ${nextTarget.availability}")
                nextTarget.currentAvailability = nextTarget.availability
                nextTarget.delayS *= 2
                if (nextTarget.delayS > 600) nextTarget.delayS = 600
            } else {
                if (nextTarget.availability == TargetAvailability.RESPONDS) {
                    nextTarget.delayS = 300
                } else if (prevAvailability != TargetAvailability.RESPONDS) {
                    nextTarget.delayS = 30
                } else {
                    nextTarget.delayS *= 2
                    if (nextTarget.delayS > 600) nextTarget.delayS = 600
                }
            }

            if (nextTarget.availability != prevAvailability) {
                onAvailabilityChange?.invoke(nextTarget.pingable.id, nextTarget.availability)
            }

            nextTarget.nextPing = Date(now().time + nextTarget.delayS * 1000)
            logDebug("Pinger::action Next ping ${nextTarget.nextPing}")
        }


        val nextActionTime =
            if (nextRequest == null || pingTargets.any { it.nextPing == null }) {
                Date(now().time + 5000)
            } else
                calcNextActionTime(
                    listOf(
                        listOf(nextRequest),
                        pingTargets.filter { it.nextPing != null }.map { it.nextPing }).flatten().filterNotNull(),
                    now()
                )
        return nextActionTime
    }

    private fun checkTarget(target: PingTarget) {
        engine.sender.sendMessageToId(
            target.pingable.id,
            mapOf("app" to "ping", "type" to "ping", "senderId" to engine.currentUser().first)
        )
        { progress, _ ->
            when {
                progress == SendProgress.SENDING && target.availability <= TargetAvailability.OFFLINE -> {
                    target.currentAvailability = TargetAvailability.RESOLVED
                    if (target.availability < TargetAvailability.RESOLVED) target.availability =
                        TargetAvailability.RESOLVED
                }
                progress == SendProgress.SENT -> {
                    target.currentAvailability = TargetAvailability.CONNECTS
                    if (target.availability < TargetAvailability.CONNECTS) target.availability =
                        TargetAvailability.CONNECTS
                }
                progress == SendProgress.FAILED -> {
                    target.currentAvailability = TargetAvailability.OFFLINE
                    target.availability = TargetAvailability.OFFLINE
                }
            }
        }
    }

    private fun reloadPingables() {
        pingTargets = requestPingables?.invoke()?.map { pingable ->
            PingTarget(pingable)
        } ?: emptyList()
        logDebug("Reloaded ${pingTargets}")
    }

    fun onResponse(message: Map<String, Any?>) {
        logDebug("Pinger::onResponse ${message}")
        val respondingTarget = pingTargets.find { it.pingable.id == message["verifiedId"] }
        if (respondingTarget == null) {
            System.err.printf("Ping response from unknown sender ${message["verifiedId"]}")
            return
        }

        if (respondingTarget.availability != TargetAvailability.RESPONDS)
            onAvailabilityChange?.invoke(respondingTarget.pingable.id, TargetAvailability.RESPONDS)
        respondingTarget.availability = TargetAvailability.RESPONDS
        respondingTarget.currentAvailability = TargetAvailability.RESPONDS
    }

    companion object {
        fun calcNextActionTime(times: List<Date>, now: Date): Date = times.minOrNull() ?: Date(now.time + 2000)
    }
}
