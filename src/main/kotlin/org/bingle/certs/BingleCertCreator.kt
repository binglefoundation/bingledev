package org.unknown.comms.certs

import org.unknown.comms.interfaces.IChainAccess

class BingleCertCreator(algoOps: IChainAccess, val id: String) : CertCreator() {

    val serverEncryptionCert: ByteArray

    val serverEncryptionKey: ByteArray

    val serverSigningCert: ByteArray
    val serverSigningKey: ByteArray
    val caCert: ByteArray
    val clientCert: ByteArray
    val clientKey: ByteArray

    init {
        val caPrivateKeyBytes = algoOps.privateKeyBytes()
        val caPublicKeyBytes = algoOps.publicKeyBytes()
        caCert = generateCABytes(id, caPrivateKeyBytes, caPublicKeyBytes)
        val (encCert, encKey) = generateRSACertBytes(
            id,
            caPrivateKeyBytes,
            CertUsages.SERVER_ENCRYPTION
        )
        serverEncryptionCert = encCert
        serverEncryptionKey = encKey
        
        val (signCert, signKey) = generateRSACertBytes(
            id,
            caPrivateKeyBytes,
            CertUsages.SERVER_SIGNING
        )
        serverSigningCert = signCert
        serverSigningKey = signKey

        val (clientCertBytes, clientKeyBytes) = generateRSACertBytes(id, caPrivateKeyBytes, CertUsages.CLIENT)
        clientCert = clientCertBytes
        clientKey = clientKeyBytes
    }
}