package org.unknown.comms.blockchain

import org.unknown.comms.blockchain.generated.AlgoConfig
import org.unknown.comms.interfaces.IChainAccess
import org.unknown.comms.interfaces.IKeyProvider

class AlgoAssetAccess(keyProvider: IKeyProvider? = null, algoProviderConfig: AlgoProviderConfig? = null) :
    AlgoSwap(
        SwapConfig(AlgoConfig.appId!!, AlgoConfig.creatorAddress, AlgoConfig.assetId),
        keyProvider,
        algoProviderConfig = algoProviderConfig
    ),
    IChainAccess
