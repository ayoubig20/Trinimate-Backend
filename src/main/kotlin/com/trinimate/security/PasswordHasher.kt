package com.trinimate.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Base64

class PasswordHasher {
    private val random = SecureRandom()
    private val iterations = 120_000

    fun hash(password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val key = derive(password, salt)
        return "${Base64.getEncoder().encodeToString(salt)}\$${Base64.getEncoder().encodeToString(key)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val (saltB64, hashB64) = stored.split("$")
        val salt = Base64.getDecoder().decode(saltB64)
        val expected = Base64.getDecoder().decode(hashB64)
        val actual = derive(password, salt)
        return actual.contentEquals(expected)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}