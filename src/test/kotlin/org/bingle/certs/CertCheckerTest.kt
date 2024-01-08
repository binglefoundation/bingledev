package org.bingle.certs

import org.assertj.core.api.Assertions.assertThat

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File

class CertCheckerTest {

    @Test
    fun `Can read and validate a CA cert`() {
        val cc = CertChecker(File("src/test/resources/testmat/expected_ca_cert.pem").inputStream())
        assertThat(cc.hasValidSignature()).isTrue
        assertThat(cc.issuer).isEqualTo("CN=WDO2ZN3SH3SJKEIZ3A7KHCOQKPH3LSH334AWRFCWIDLPRDKAXG5FEKMD5E.ids.bingler.net")
    }

    @Test
    fun `Can read and validate a cert chain`() {
        val caCertChecker = CertChecker(File("src/test/resources/testmat/expected_ca_cert.pem").inputStream())

        val ct = CertCreatorTest()
        val cc = CertCreator()
        val (serverCert, _) = cc.generateRSACertX509(ct.address, ct.privateKeyBytes, CertUsages.SERVER_ENCRYPTION)

        val serverCertBytes = cc.x509CertToBytes(serverCert)
        val certChecker = CertChecker(ByteArrayInputStream(serverCertBytes))
        assertThat(certChecker.hasValidSignature(caCertChecker.subjectPublicKeyBytes)).isTrue()
        assertThat(certChecker.issuer).isEqualTo("CN=WDO2ZN3SH3SJKEIZ3A7KHCOQKPH3LSH334AWRFCWIDLPRDKAXG5FEKMD5E.ids.bingler.net")
        assertThat(certChecker.issuer).isEqualTo(caCertChecker.issuer)
    }

    @Test
    fun `Fails for an invalid ca cert`() {
        val ccBytes = File("src/test/resources/testmat/expected_ca_cert.pem").readBytes()
        ccBytes[ccBytes.size - 64] = 55
        val invalidCC = CertChecker(ByteArrayInputStream(ccBytes))
        assertThat(invalidCC.hasValidSignature()).isFalse()
    }
}