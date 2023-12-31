package org.bingle.engine.ddb

// TODO : change the boot and attach flow
//
class DistributedDBServer(myId: String,
                          lookupRootRelay: () -> String,
                          sendForResponse: (toId: String, message: Map<String, Any?>) -> Map<String, Any?>,
                          val send: (id: String, command: Map<String, Any>) -> Boolean) :
                          DistributedDBClient(myId, lookupRootRelay, sendForResponse) {

    lateinit var distributedDB: DistributedDB

    override fun connect() {
        super.connect()

        val initResponseRecord = myDbServerId?.let {
            sendForResponse(it, mapOf("type" to "initResolve"))
        } ?: throw RuntimeException("client connect did not connect to a server")

        if(initResponseRecord["fail"] != null) throw RuntimeException("initResolveResponse failed ${initResponseRecord["fail"]}")

        relayPlan?.let {
            distributedDB = DistributedDB(myId, it )

            distributedDB.enterLoadingState(initResponseRecord["dbCount"] as Int)
        } ?: throw RuntimeException("client connect did not set a relay plan")
    }

}