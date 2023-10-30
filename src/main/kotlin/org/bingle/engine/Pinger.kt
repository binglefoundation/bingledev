package org.unknown.comms

import com.creatotronik.util.logDebug
import org.unknown.comms.interfaces.ICommsSender
import java.util.*
import kotlin.concurrent.timerTask

class Pinger(
    val comms: ICommsSender,
    val requestPingables: () -> List<Pingable>,
    val onAvailabilityChange: (id: String, availability: TargetAvailability) -> Unit,
    val now: (() -> Date) = { Date() }
) {

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

    var pingTargets: List<PingTarget> = emptyList()
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
                onAvailabilityChange(nextTarget.pingable.id, nextTarget.availability)
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
        comms.sendMessageToId(
            target.pingable.id,
            mapOf("app" to "ping", "type" to "ping", "senderId" to comms.currentUser().first)
        )
        { progress, _ ->
            when {
                progress == Comms.SendProgress.SENDING && target.availability <= TargetAvailability.OFFLINE -> {
                    target.currentAvailability = TargetAvailability.RESOLVED
                    if (target.availability < TargetAvailability.RESOLVED) target.availability =
                        TargetAvailability.RESOLVED
                }
                progress == Comms.SendProgress.SENT -> {
                    target.currentAvailability = TargetAvailability.CONNECTS
                    if (target.availability < TargetAvailability.CONNECTS) target.availability =
                        TargetAvailability.CONNECTS
                }
                progress == Comms.SendProgress.FAILED -> {
                    target.currentAvailability = TargetAvailability.OFFLINE
                    target.availability = TargetAvailability.OFFLINE
                }
            }
        }
    }

    private fun reloadPingables() {
        pingTargets = requestPingables().map { pingable ->
            PingTarget(pingable)
        }
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
            onAvailabilityChange(respondingTarget.pingable.id, TargetAvailability.RESPONDS)
        respondingTarget.availability = TargetAvailability.RESPONDS
        respondingTarget.currentAvailability = TargetAvailability.RESPONDS
    }

    companion object {
        fun calcNextActionTime(times: List<Date>, now: Date): Date = times.minOrNull() ?: Date(now.time + 2000)
    }
}
