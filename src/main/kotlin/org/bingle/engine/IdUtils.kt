package org.unknown.comms

class IdUtils(val id: String) {
    enum class IdLike {ISSUER}

    constructor(issuer: String, what: IdLike) : this(
        when(what) {
            IdLike.ISSUER -> issuer.replace(Regex("CN=([A-Z0-9]+)\\.(?:(?:(?!-))(?:xn--|_{1,1})?[a-z0-9-]{0,61}[a-z0-9]{1,1}\\.)*(?:xn--)?(?:[a-z0-9][a-z0-9\\-]{0,60}|[a-z0-9-]{1,30}\\.[a-z]{2,})$"), "$1")
        })

    companion object {
        fun fromIssuer(issuer: String): IdUtils =
            IdUtils(issuer, IdLike.ISSUER)
    }
}