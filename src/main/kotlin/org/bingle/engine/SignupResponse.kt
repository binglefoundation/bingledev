package org.unknown.comms

data class SignupResponse(val keyState: KeyState, val address: String, val privateKey: String) {
    enum class KeyState {
        NEWKEY,
        PAID,
        UNPAID
    }
}
