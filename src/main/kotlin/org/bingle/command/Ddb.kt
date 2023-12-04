package org.bingle.command

import com.beust.klaxon.*
import org.bingle.command.data.AdvertRecord

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
open class Ddb() : BaseCommand() {

    open class Update(val startId: String, val epoch: Int) : Ddb()

    class UpsertResolve(val record: AdvertRecord, startId: String, epoch: Int) :
        Update(startId, epoch)

    class DeleteResolve(startId: String, epoch: Int) : Update(startId, epoch)
    class QueryResolve(val id: String) :
        Ddb()

    class Signon(val startId: String) : Ddb()
    class GetEpoch(val epochId: Int) :
        Ddb()

    class InitResolve(val senderId: String) : Ddb()
    class DumpResolve(val senderId: String, val record: AdvertRecord) :
        Ddb()

    class UpdateResponse() : Ddb()

    class GetEpochResponse(
        val epochId: Int,
        val treeOrder: Int,
        val relayIds: List<String>
    ) : Ddb()

    class QueryResponse(val found: Boolean, val advert: AdvertRecord? = null) :
        Ddb()

    class InitResponse(val dbCount: Int) : Ddb()

    class DumpResolveResponse(
        val recordIndex: Int,
        val recordId: String,
        val record: AdvertRecord
    ) : Ddb()
}