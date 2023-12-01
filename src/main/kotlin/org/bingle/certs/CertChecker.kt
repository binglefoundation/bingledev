package org.bingle.certs

import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers
import org.bouncycastle.asn1.x509.Certificate
import org.bouncycastle.asn1.x509.TBSCertificate
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.io.pem.PemReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader

class CertChecker(certStream: InputStream? = null) {
    constructor(cert: Certificate) : this() {
        x509CertificateHolder = X509CertificateHolder(cert)
    }

    private lateinit var x509CertificateHolder: X509CertificateHolder

    init {
        if(certStream != null) {
            val pemReader = PemReader(InputStreamReader(certStream))
            x509CertificateHolder = X509CertificateHolder(pemReader.readPemObject().content)
        }
    }

    val issuer: String
        get() = x509CertificateHolder.issuer.toString()

    val pemBytes: ByteArray
        get() {
            val cc = CertCreator()
            return cc.x509CertToBytes(cc.x509HolderToCertificate(x509CertificateHolder))
        }

    fun hasValidSignature(ed25519PublicKeyBytes: ByteArray? = null): Boolean {
        if(x509CertificateHolder.signatureAlgorithm.algorithm == EdECObjectIdentifiers.id_Ed25519) {
            val tbsCert: TBSCertificate = x509CertificateHolder.toASN1Structure().getTBSCertificate()
            val tbsCertStream = ByteArrayOutputStream()
            tbsCert.encodeTo(tbsCertStream, ASN1Encoding.DER)
            tbsCertStream.close()
            val tbsCertBytes = tbsCertStream.toByteArray()

            val signature = x509CertificateHolder.signature
            val verifier = Ed25519Signer()
            val publicKeyBytes = ed25519PublicKeyBytes ?: tbsCert.subjectPublicKeyInfo.publicKeyData.bytes
            val ed25519PublicKeyParameters =
                Ed25519PublicKeyParameters(publicKeyBytes, 0)
            verifier.init(false, ed25519PublicKeyParameters)
            verifier.update(tbsCertBytes, 0, tbsCertBytes.size)
            return verifier.verifySignature(signature)
        }

        throw RuntimeException("${x509CertificateHolder.signatureAlgorithm.algorithm.id} not supported here yet")
    }

    val subjectPublicKeyBytes: ByteArray
        get() = x509CertificateHolder.toASN1Structure().getTBSCertificate().subjectPublicKeyInfo.publicKeyData.bytes
}