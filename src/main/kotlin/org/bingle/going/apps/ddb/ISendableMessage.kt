package org.bingle.going.apps.ddb

interface ISendableMessage {
    fun toMap(): Map<String, Any>
}
