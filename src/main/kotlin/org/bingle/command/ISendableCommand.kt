package org.bingle.command

interface ISendableCommand {
    fun toMap(): Map<String, Any>
}
