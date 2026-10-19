package dev.zenda.inputbridge.lab.adb

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Experimental Phase 1 wrapper around libadb-android. The ADB key's private
 * bytes are encrypted with an Android Keystore AES-GCM key before persistence.
 * This code has not yet been audited or validated on a physical device.
 */
class AdbConnectionManager(context: Context) : AbsAdbConnectionManager() {
    companion object {
        @Volatile private var sharedInstance: AdbConnectionManager? = null

        /** Share one ADB identity/connection manager between Activity and pairing Service in this process. */
        fun shared(context: Context): AdbConnectionManager = synchronized(this) {
            sharedInstance ?: AdbConnectionManager(context.applicationContext).also { sharedInstance = it }
        }

        /** Detach the process singleton before closing, so a later activation can create a fresh manager. */
        fun releaseShared(): AdbConnectionManager? = synchronized(this) {
            sharedInstance.also { sharedInstance = null }
        }
    }

    private val appContext = context.applicationContext
    private val identityFile = File(appContext.filesDir, "adb-identity-v1.bin")
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(15, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
        val identity = loadOrCreateIdentity()
        privateKey = identity.first
        certificate = identity.second
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "InputBridge-Phase1"

    @Synchronized
    @Throws(IOException::class, InterruptedException::class)
    fun pairNow(host: String, pairingPort: Int, pairingCode: String): Boolean {
        requireValidEndpoint(host, pairingPort)
        require(pairingCode.matches(Regex("\\d{6}"))) { "Pairing code must contain six digits." }
        return pair(host.trim(), pairingPort, pairingCode)
    }

    @Synchronized
    @Throws(IOException::class, InterruptedException::class)
    fun connectNow(host: String, connectionPort: Int): Boolean {
        requireValidEndpoint(host, connectionPort)
        return connect(host.trim(), connectionPort)
    }

    /** Only fixed, bounded commands used by the Lab are permitted here. */
    @Throws(IOException::class, InterruptedException::class)
    fun runFixedProbeCommand(command: String): String {
        require(ProbeInputPolicy.isAllowedCommand(command)) { "Only fixed shell identity, package-path lookup, and helper-bootstrap commands are allowed." }
        val stream: AdbStream = openStream("shell:$command")
        return try {
            stream.openInputStream().bufferedReader().use { it.readText().take(4000) }.trim()
        } finally {
            try { stream.close() } catch (_: Exception) { }
        }
    }

    /** Starts the app-process helper only; input requests travel over its local socket afterward. */
    @Throws(IOException::class, InterruptedException::class)
    fun bootstrapHelper(port: Int, token: String): String {
        require(ProbeInputPolicy.isValidHelperPort(port)) { "Helper port is outside the allowed range." }
        require(token.matches(Regex("[0-9a-f]{64}"))) { "Helper session token format is invalid." }
        val pathOutput = runFixedProbeCommand("pm path dev.zenda.inputbridge.lab")
        val apkPath = pathOutput.lineSequence()
            .map { it.trim().removePrefix("package:") }
            .firstOrNull { it.startsWith("/data/app/") && it.endsWith("/base.apk") }
            ?: throw IOException("Package manager did not return the Lab base APK path.")
        require(apkPath.matches(Regex("/data/app/[A-Za-z0-9_./~=-]+/base\\.apk"))) {
            "Package manager returned an unexpected APK path format."
        }
        val launchCommand = "CLASSPATH=$apkPath /system/bin/app_process /system/bin dev.zenda.inputbridge.lab.adb.AdbBootstrapHelperMain $port $token </dev/null >/dev/null 2>&1 &"
        return runFixedProbeCommand(launchCommand)
    }

    private fun requireValidEndpoint(host: String, port: Int) {
        require(host.isNotBlank() && host.length <= 253) { "Enter a valid device IP/host." }
        require(ProbeInputPolicy.isValidPort(port)) { "Port must be between 1 and 65535." }
        require(!host.any { it.isWhitespace() }) { "Host must not contain whitespace." }
    }

    private fun loadOrCreateIdentity(): Pair<PrivateKey, Certificate> {
        if (identityFile.exists()) {
            try {
                val decrypted = decrypt(identityFile.readBytes())
                val input = DataInputStream(ByteArrayInputStream(decrypted))
                val keyLength = input.readInt()
                require(keyLength in 1..16384)
                val keyBytes = ByteArray(keyLength).also(input::readFully)
                val certLength = input.readInt()
                require(certLength in 1..16384)
                val certBytes = ByteArray(certLength).also(input::readFully)
                require(input.available() == 0)
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certBytes))
                return key to cert
            } catch (_: Exception) {
                // If the wrapping key was invalidated, discard only this app's damaged ADB identity.
                identityFile.delete()
            }
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val keyPair = generator.generateKeyPair()
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=InputBridge Phase 1")
        val info = X509CertInfo().apply {
            set("version", CertificateVersion(2))
            set("serialNumber", CertificateSerialNumber(BigInteger.valueOf((now and Long.MAX_VALUE).coerceAtLeast(1L))))
            set("algorithmID", CertificateAlgorithmId(AlgorithmId.get("SHA256withRSA")))
            set("subject", subject)
            set("key", CertificateX509Key(keyPair.public))
            set("validity", CertificateValidity(Date(now - 60_000), Date(now + 365L * 24 * 60 * 60 * 1000)))
            set("issuer", subject)
        }
        val signedCertificate = X509CertImpl(info).apply { sign(keyPair.private, "SHA256withRSA") }
        val payloadBytes = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                val keyBytes = keyPair.private.encoded
                val certBytes = signedCertificate.encoded
                output.writeInt(keyBytes.size); output.write(keyBytes)
                output.writeInt(certBytes.size); output.write(certBytes)
            }
            bytes.toByteArray()
        }
        identityFile.writeBytes(encrypt(payloadBytes))
        return keyPair.private to signedCertificate
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "zenda.inputbridge.adb.identity.wrap.v1"
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val cipherText = cipher.doFinal(plainText)
        return ByteArrayOutputStream().use { out ->
            out.write(cipher.iv)
            out.write(cipherText)
            out.toByteArray()
        }
    }

    private fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size > 12 + 16)
        val iv = encrypted.copyOfRange(0, 12)
        val cipherText = encrypted.copyOfRange(12, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }
}
