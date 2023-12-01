package org.bingle.certs

import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import java.io.*
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*


enum class CertUsages {
    SERVER_SIGNING, SERVER_ENCRYPTION, CLIENT
}

open class CertCreator {
    init {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    private val algorithmIdentifierEd25519 = AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519)

    fun validate(certBytes: ByteArray): Boolean {
        val pemParser = PEMParser(InputStreamReader(ByteArrayInputStream(certBytes)))
        val x509holder = pemParser.readObject() as X509CertificateHolder

        val issuerCN = x509holder.issuer.rdNs.flatMap { it.typesAndValues.asList() }
            .find { it.type == X509ObjectIdentifiers.commonName }
        if (issuerCN == null) {
            System.err.println("Certificate has no CN in ${x509holder.issuer}")
            return false
        }
        val issuerID = issuerCN.value.toString().split(".").firstOrNull()
        if (issuerID == null) {
            System.err.println("Issuer CN not in format id.domain ${x509holder.issuer}")
            return false
        }

        println("IssuerID ${issuerID}")

        val contentVerifierBuilder = JcaContentVerifierProviderBuilder()
        val contentVerifierProvider = contentVerifierBuilder.build(x509holder)

        return x509holder.isSignatureValid(contentVerifierProvider)
    }

    fun generateCA(
        address: String,
        privateKeyBytes: ByteArray,
        publicKeyBytes: ByteArray,
        randomSerial: Int? = null
    ) {
        val x509: X509Certificate = generateCAX509(address, privateKeyBytes, publicKeyBytes, randomSerial)

        // serialize in PEM format
        val pemWriter = JcaPEMWriter(FileWriter("tmp/ca_cert.pem"))
        pemWriter.writeObject(x509)
        pemWriter.close()
    }

    fun generateCABytes(
        address: String,
        privateKeyBytes: ByteArray,
        publicKeyBytes: ByteArray,
        useSerial: Int? = null
    ): ByteArray {
        val x509: X509Certificate = generateCAX509(address, privateKeyBytes, publicKeyBytes, useSerial)

        return x509CertToBytes(x509)
    }

    fun x509CertToBytes(x509: X509Certificate): ByteArray {
        // serialize in PEM format and return bytes
        val resStream = ByteArrayOutputStream()
        val pemWriter = JcaPEMWriter(OutputStreamWriter(resStream))
        pemWriter.writeObject(x509)
        pemWriter.close()

        return resStream.toByteArray()
    }

    fun generateRSACertBytes(
        address: String,
        caPrivateKeyBytes: ByteArray,
        usage: CertUsages,
        randomSerial: Int? = null
    ): Pair<ByteArray, ByteArray> {
        val (x509cert, key) = generateRSACertX509(address, caPrivateKeyBytes, usage, randomSerial)
        val certByteArray = x509CertToBytes(x509cert)

        // Also key as PEM private key
        val resKeyStream = ByteArrayOutputStream()
        generatePrivateKeyPemStream(key, resKeyStream) { Pair(KeyFactory.getInstance("RSASSA-PSS"), it) }

        return Pair(certByteArray, resKeyStream.toByteArray())
    }

    fun generateCAX509(
        address: String,
        privateKeyBytes: ByteArray,
        publicKeyBytes: ByteArray,
        randomSerial: Int? = null
    ): X509Certificate {
        val random = SecureRandom()
        val (keyFactory, privateKey) = generatePrivateEd25519(privateKeyBytes)

        val publicKeyInfo = SubjectPublicKeyInfo(algorithmIdentifierEd25519, publicKeyBytes)
        val x509KeySpec = X509EncodedKeySpec(publicKeyInfo.encoded)
        val jcaPublicKey = keyFactory.generatePublic(x509KeySpec)

        // fill in certificate fields
        val subject: X500Name = X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, "$address.ids.bingler.net")
            .build()

        val serial = createId(randomSerial, random)

        val certificate: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            Date(),
            datePlusOne(),
            subject,
            jcaPublicKey
        )
        // certificate.addExtension(Extension.subjectKeyIdentifier, false, id)
        // certificate.addExtension(Extension.authorityKeyIdentifier, false, id)
        val constraints = BasicConstraints(true)
        certificate.addExtension(
            Extension.basicConstraints,
            true,
            constraints.encoded
        )
        val usage = KeyUsage(KeyUsage.keyCertSign or KeyUsage.digitalSignature)
        certificate.addExtension(Extension.keyUsage, false, usage.encoded)
        val usageEx = ExtendedKeyUsage(
            arrayOf<KeyPurposeId>(
                KeyPurposeId.id_kp_serverAuth,
                KeyPurposeId.id_kp_clientAuth
            )
        )
        certificate.addExtension(
            Extension.extendedKeyUsage,
            false,
            usageEx.encoded
        )

        // build BouncyCastle certificate
        // Implies SHA-512 https://en.wikipedia.org/wiki/EdDSA
        val signer: ContentSigner = JcaContentSignerBuilder("ed25519")
            .build(privateKey)
        val holder = certificate.build(signer)

        return x509HolderToCertificate(holder)
    }

    fun x509HolderToCertificate(holder: X509CertificateHolder): X509Certificate {
        // convert to JRE certificate
        val converter = JcaX509CertificateConverter()
        converter.setProvider(BouncyCastleProvider())
        return converter.getCertificate(holder)
    }

    private fun createId(
        randomSerial: Int?,
        random: SecureRandom,
    ): BigInteger {
        if (randomSerial == null) {
            val id = ByteArray(20)
            random.nextBytes(id)
            return BigInteger(160, random)
        }

        return BigInteger(randomSerial.toString())
    }

    fun generateRSACertX509(
        address: String,
        caPrivateKeyBytes: ByteArray,
        usage: CertUsages,
        randomSerial: Int? = null
    ): Pair<X509Certificate, PrivateKey> {
        // Generates an RSA cert signed in ed25519
        val (_, caPrivateKey) = generatePrivateEd25519(caPrivateKeyBytes)

        val random = SecureRandom()
        val keypairGen = KeyPairGenerator.getInstance("RSASSA-PSS")
        keypairGen.initialize(4096, random)
        val keypair = keypairGen.generateKeyPair()

        // fill in certificate fields
        val subject: X500Name = X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, "$address.ids.bingler.net")
            .build()

        val serial = createId(randomSerial, random)

        val certificate: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            Date(),
            datePlusOne(),
            subject,
            keypair.public
        )
        // certificate.addExtension(Extension.subjectKeyIdentifier, false, id)
        // certificate.addExtension(Extension.authorityKeyIdentifier, false, id)
        val constraints = BasicConstraints(false)
        certificate.addExtension(
            Extension.basicConstraints,
            false,
            constraints.encoded
        )

        val keyUsage =
            KeyUsage(if (usage == CertUsages.SERVER_SIGNING || usage == CertUsages.CLIENT) (KeyUsage.keyCertSign or KeyUsage.digitalSignature) else KeyUsage.keyEncipherment)
        certificate.addExtension(Extension.keyUsage, false, keyUsage.encoded)
        val usageEx = ExtendedKeyUsage(
            arrayOf<KeyPurposeId>(
                KeyPurposeId.id_kp_serverAuth,
                KeyPurposeId.id_kp_clientAuth
            )
        )
        certificate.addExtension(
            Extension.extendedKeyUsage,
            false,
            usageEx.encoded
        )

        // build BouncyCastle certificate
        val signer: ContentSigner = JcaContentSignerBuilder("ed25519")
            .build(caPrivateKey)
        val holder = certificate.build(signer)

        // convert to JRE certificate
        val converter = JcaX509CertificateConverter()
        converter.setProvider(BouncyCastleProvider())
        return Pair(converter.getCertificate(holder), keypair.private)
    }

    private fun datePlusOne(): Date {
        val calPlus24 = Calendar.getInstance()
        calPlus24.time = Date()
        calPlus24.add(Calendar.DATE, 1)
        return calPlus24.time
    }

//    private fun generatePrivateRSA(privateKey: PrivateKey): Pair<KeyFactory, PrivateKey> {
//        val keyFactory = KeyFactory.getInstance("RSA")
//
//        val keySpec = PKCS8EncodedKeySpec(
//            PrivateKeyInfo(
//                AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption),
//                DEROctetString(privateKeyBytes)
//            ).encoded
//        )
//        val privateKey = keyFactory.generatePrivate(keySpec)
//
//        return Pair(keyFactory, privateKey)
//    }

    private fun generatePrivateEd25519(privateKeyBytes: ByteArray): Pair<KeyFactory, PrivateKey> {
        val keyFactory = KeyFactory.getInstance("Ed25519")

        val privateKey = keyFactory.generatePrivate(
            PKCS8EncodedKeySpec(
                PrivateKeyInfo(
                    algorithmIdentifierEd25519,
                    DEROctetString(privateKeyBytes)
                ).encoded
            )
        )
        return Pair(keyFactory, privateKey)
    }

    fun generateRSAPrivateKeyFile(privateKey: PrivateKey, filename: String) {
        val fs = FileOutputStream(filename)
        generatePrivateKeyPemStream(privateKey, fs) { Pair(KeyFactory.getInstance("RSASSA-PSS"), it) }
        fs.close()
    }

    fun generateEDPrivateKeyFile(privateKeyBytes: ByteArray, filename: String) {
        val fs = FileOutputStream(filename)
        generatePrivateKeyPemStream(privateKeyBytes, fs) { generatePrivateEd25519(it) }
        fs.close()
    }

    fun <T> generatePrivateKeyPemStream(
        privateKey: T, resStream: OutputStream,
        keyObjectGen: (k: T) -> Pair<KeyFactory, PrivateKey>
    ) {
        val printWriter = PrintWriter(resStream)
        printWriter.println("-----BEGIN PRIVATE KEY-----")

        val (_, privateKeyObject) = keyObjectGen(privateKey)

        val pemWriter = JcaPEMWriter(OutputStreamWriter(resStream))
        pemWriter.writeObject(privateKeyObject)
        pemWriter.flush()

        printWriter.println("-----END PRIVATE KEY-----")
    }
}

