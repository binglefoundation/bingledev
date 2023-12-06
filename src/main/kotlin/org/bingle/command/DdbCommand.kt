package org.bingle.command

import com.beust.klaxon.*
import org.bingle.command.data.AdvertRecord

@TypeFor(field = "type", adapter = BaseCommand.BaseTypeAdapter::class)
open class DdbCommand() : BaseCommand() {

    open class Update(val startId: String, val epoch: Int) : DdbCommand()
    class UpdateResponse() : DdbCommand()
    class PendingResponse(): DdbCommand()

    class UpsertResolve(val record: AdvertRecord, startId: String, epoch: Int) :
        Update(startId, epoch)

    class DeleteResolve(startId: String, epoch: Int) : Update(startId, epoch)
    class QueryResolve(val id: String) :
        DdbCommand()

    class Signon(val startId: String) : DdbCommand()
    class GetEpoch(val epochId: Int) :
        DdbCommand()

    class InitResolve(val senderId: String) : DdbCommand()
    class DumpResolve(val senderId: String, val record: AdvertRecord) :
        DdbCommand()


    class GetEpochResponse(
        val epochId: Int,
        val treeOrder: Int,
        val relayIds: List<String>
    ) : DdbCommand()

    class QueryResponse(val found: Boolean, val advert: AdvertRecord? = null) :
        DdbCommand()

    class InitResponse(val dbCount: Int) : DdbCommand()

    class DumpResolveResponse(
        val recordIndex: Int,
        val recordId: String,
        val record: AdvertRecord
    ) : DdbCommand()

}