package org.bingle.certs

import com.lordcodes.turtle.shellRun
import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions.assertThat
import org.bingle.blockchain.AlgoOps
import org.junit.jupiter.api.Test
import java.io.File

class CertCreatorTest {
    val passphrase =
        "click coin purpose bulk spice clay become exhaust stick pet viable ocean vacuum hand draw banana route access couch sadness almost own brand above swear"
    val address =
        "WDO2ZN3SH3SJKEIZ3A7KHCOQKPH3LSH334AWRFCWIDLPRDKAXG5FEKMD5E"

    val publicKeyBytes = Hex.decodeHex(TestKeyMat().publicKeyHex)
    val privateKeyBytes = Hex.decodeHex(TestKeyMat().privateKeyHex)

    @Test
    fun `generates a sustificate`() {
        val algoOps = AlgoOps(null, passphrase, address)
        CertCreator().generateCA(address, algoOps.privateKeyBytes(), algoOps.publicKeyBytes())
        // into tmp/mycert.pem
    }

    @Test
    fun `validates a sustificate`() {
        val algoOps = AlgoOps(null, passphrase, address)
        val certCreator = CertCreator()
        val certBytes = certCreator.generateCABytes(address, algoOps.privateKeyBytes(), algoOps.publicKeyBytes())

        val validateRes = certCreator.validate(certBytes)
        assert(validateRes)
    }
    
    @Test
    fun `generates ED25519 cert and key using resource data`() {
        val certCreator = CertCreator()
        certCreator.generateCA(address, privateKeyBytes, publicKeyBytes, 1)
        // TODO: fix the date
        //assertThat(File("tmp/ca_cert.pem"))
        //    .hasSameTextualContentAs(File("src/test/resources/testmat/expected_ca_cert.pem"))
        certCreator.generateEDPrivateKeyFile(privateKeyBytes, "tmp/private_key.pem")
        assertThat(File("tmp/private_key.pem"))
            .hasContent(File("src/main/resources/com/creatotronik/dtls/x509-ca-key-ed25519.pem")
                .readText()
                .replace(Regex("^.+(-----BEGIN PRIVATE KEY-----.+$)", RegexOption.DOT_MATCHES_ALL), "$1"))
    }

    @Test
    fun `generates RSA server cert and key (signing) using resource data`() {
        val certCreator = CertCreator()
        certCreator.generateCA(address, privateKeyBytes, publicKeyBytes)

        val (serverCertBytes, serverPrivateKeyBytes) = certCreator.generateRSACertBytes(address, privateKeyBytes, CertUsages.SERVER_SIGNING)

        File( "tmp/server_cert.pem").writeBytes(serverCertBytes)
        val certPrints = shellRun("bash", listOf("-c", "/usr/local/bin/gnutls-certtool -i --infile=tmp/server_cert.pem | grep -A2 'Public Key ID' | tail -2 | sort | xargs -L1 echo"))

        File( "tmp/server_key.pem").writeBytes(serverPrivateKeyBytes)
        val keyPrints = shellRun("bash", listOf("-c", "/usr/local/bin/gnutls-certtool -k --infile=tmp/server_key.pem | grep -A2 'Public Key ID' | tail -2 | sort | xargs -L1 echo"))
        assertThat(certPrints).isEqualTo(keyPrints)
    }

    @Test
    fun `generates server cert (encryption) using resource data`() {
        val certCreator = CertCreator()
        certCreator.generateCA(address, privateKeyBytes, publicKeyBytes)

        val (serverCertBytes, serverPrivateKeyBytes) = certCreator.generateRSACertBytes(address, privateKeyBytes, CertUsages.SERVER_ENCRYPTION)

        File( "tmp/server_cert.pem").writeBytes(serverCertBytes)
        val certPrints = shellRun("bash", listOf("-c", "/usr/local/bin/gnutls-certtool -i --infile=tmp/server_cert.pem | grep -A2 'Public Key ID' | tail -2 | sort | xargs -L1 echo"))

        File( "tmp/server_key.pem").writeBytes(serverPrivateKeyBytes)
        val keyPrints = shellRun("bash", listOf("-c", "/usr/local/bin/gnutls-certtool -k --infile=tmp/server_key.pem | grep -A2 'Public Key ID' | tail -2 | sort | xargs -L1 echo"))
        assertThat(certPrints).isEqualTo(keyPrints)
    }
}