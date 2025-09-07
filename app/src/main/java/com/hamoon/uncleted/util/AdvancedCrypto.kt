package com.hamoon.uncleted.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

object AdvancedCrypto {

    private const val TAG = "AdvancedCrypto"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "UncleTedMasterKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 16

    data class EncryptedData(
        val cipherText: String,
        val iv: String,
        val tag: String? = null
    )

    init {
        initializeKeystore()
    }

    private fun initializeKeystore() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize keystore", e)
        }
    }

    private fun generateKey() {
        try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false) // Set to true for biometric-protected keys
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()

            Log.i(TAG, "Master encryption key generated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate encryption key", e)
            throw e
        }
    }

    fun encryptSensitiveData(plaintext: String): EncryptedData? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            EncryptedData(
                cipherText = Base64.encodeToString(cipherText, Base64.DEFAULT),
                iv = Base64.encodeToString(iv, Base64.DEFAULT)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt data", e)
            null
        }
    }

    fun decryptSensitiveData(encryptedData: EncryptedData): String? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)

            val iv = Base64.decode(encryptedData.iv, Base64.DEFAULT)
            val spec = GCMParameterSpec(TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val cipherText = Base64.decode(encryptedData.cipherText, Base64.DEFAULT)
            val plaintext = cipher.doFinal(cipherText)

            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt data", e)
            null
        }
    }

    fun generateSecureToken(length: Int = 32): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val secureRandom = SecureRandom()
        return (1..length)
            .map { chars[secureRandom.nextInt(chars.length)] }
            .joinToString("")
    }

    fun hashWithSalt(data: String, salt: String? = null): String {
        val actualSalt = salt ?: generateSecureToken(16)
        val combined = data + actualSalt

        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hash, Base64.DEFAULT).trim() + ":$actualSalt"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash data", e)
            ""
        }
    }

    fun verifyHash(data: String, hashedWithSalt: String): Boolean {
        return try {
            val parts = hashedWithSalt.split(":")
            if (parts.size != 2) return false

            val originalHash = parts[0]
            val salt = parts[1]
            val newHash = hashWithSalt(data, salt).split(":")[0]

            originalHash == newHash
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify hash", e)
            false
        }
    }

    // Steganography-like data hiding in images (basic implementation)
    fun hideDataInNoise(sensitiveData: String): ByteArray {
        val encrypted = encryptSensitiveData(sensitiveData)
        if (encrypted == null) return byteArrayOf()

        val dataToHide = "${encrypted.cipherText}|${encrypted.iv}".toByteArray()
        val noiseSize = 1024 + dataToHide.size * 8 // Add significant noise
        val noise = ByteArray(noiseSize)
        SecureRandom().nextBytes(noise)

        // Hide data in the least significant bits of the noise
        for (i in dataToHide.indices) {
            val byte = dataToHide[i]
            for (bit in 0..7) {
                val bitValue = (byte.toInt() shr bit) and 1
                val noiseIndex = i * 8 + bit
                if (noiseIndex < noise.size) {
                    noise[noiseIndex] = (noise[noiseIndex].toInt() and 0xFE or bitValue).toByte()
                }
            }
        }

        return noise
    }

    fun extractDataFromNoise(noiseData: ByteArray, dataLength: Int): String? {
        return try {
            val extractedBytes = ByteArray(dataLength)

            for (i in extractedBytes.indices) {
                var byte = 0
                for (bit in 0..7) {
                    val noiseIndex = i * 8 + bit
                    if (noiseIndex < noiseData.size) {
                        val bitValue = noiseData[noiseIndex].toInt() and 1
                        byte = byte or (bitValue shl bit)
                    }
                }
                extractedBytes[i] = byte.toByte()
            }

            val dataString = String(extractedBytes, Charsets.UTF_8)
            val parts = dataString.split("|")
            if (parts.size == 2) {
                val encryptedData = EncryptedData(parts[0], parts[1])
                decryptSensitiveData(encryptedData)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract data from noise", e)
            null
        }
    }

    // Generate cryptographically secure PINs
    fun generateSecurePin(length: Int = 6): String {
        val secureRandom = SecureRandom()
        return (1..length)
            .map { secureRandom.nextInt(10) }
            .joinToString("")
    }

    // Secure comparison to prevent timing attacks
    fun secureCompare(a: String, b: String): Boolean {
        if (a.length != b.length) return false

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}