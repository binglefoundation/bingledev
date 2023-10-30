package org.unknown.comms.apps.ddb

interface ISendableMessage {
    fun toMap(): Map<String, Any>
}
