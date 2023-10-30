package org.bingle.command

import com.beust.klaxon.*
import org.bingle.command.data.AdvertRecord

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
open class Ddb() : BaseCommand() {
    @Json(serializeNull = false)
    var responseTag: String? = null

    open class Update(val startId: String, val epoch: Int) : Ddb()

    class UpsertResolve(val record: AdvertRecord, startId: String, epoch: Int) :
        Update(startId, epoch)

    class DeleteResolve(startId: String, epoch: Int) : Update(startId, epoch)
    class QueryResolve(val senderId: String, val id: String) :
        Ddb()

    class Signon(val senderId: String, val startId: String) : Ddb()
    class GetEpoch(val senderId: String, val epochId: Int) :
        Ddb()

    class InitResolve(val senderId: String) : Ddb()
    class DumpResolve(val senderId: String, val record: AdvertRecord) :
        Ddb()

    class UpdateResponse(val tag: String?) : Ddb()

    class GetEpochResponse(
        val tag: String?,
        val senderId: String,
        val epochId: Int,
        val treeOrder: Int,
        val relayIds: List<String>
    ) : Ddb()

    class QueryResponse(val tag: String?, val senderId: String, val found: Boolean, val advert: AdvertRecord? = null) :
        Ddb()

    class InitResponse(val ag: String?, val senderId: String, val dbCount: Int) : Ddb()

    class DumpResolveResponse(
        tag: String?,
        val senderId: String,
        val recordIndex: Int,
        val recordId: String,
        val record: AdvertRecord
    ) : Ddb()
}