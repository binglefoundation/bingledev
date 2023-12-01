package org.bingle.blockchain

import org.bingle.blockchain.generated.AlgoConfig
import org.bingle.interfaces.IChainAccess
import org.bingle.interfaces.IKeyProvider

class AlgoAssetAccess(keyProvider: IKeyProvider? = null, algoProviderConfig: AlgoProviderConfig? = null) :
    AlgoSwap(
        SwapConfig(AlgoConfig.appId!!, AlgoConfig.creatorAddress, AlgoConfig.assetId),
        keyProvider,
        algoProviderConfig = algoProviderConfig
    ),
    IChainAccess
