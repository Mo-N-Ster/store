package com.vibe.store.infrastructure.security

import org.junit.Assert.*
import org.junit.Test

class BcryptParityTest {
    @Test fun sourceUtf8And72ByteVectors() {
        val bcrypt = BcryptPasswords()
        // Synthetic outputs generated using the installed source bcryptjs at cost 10.
        val vectors = listOf(
            "Synthetic-Password-42" to "\$2b\$10\$a.S3FTSh8HqpZd8vUHhFDOxzKImqtUdN1jFLEtJwXIF2XVSw21/ZG",
            "éè漢字😀12345678" to "\$2b\$10\$3qaPkKbGLa0t8MSVgl.mMuLinwgTss0rIl7hwGhmswGIMbuqN9ciy",
            "x".repeat(72) to "\$2b\$10\$hShhp0/j8YILzskvuBlQdeQ5jIJiG8Wq0kRmxkzr/f4FyNz0POwKe",
            ("x".repeat(71) + "é") to "\$2b\$10\$28S5SlznPNsHhopqcWwLSOInza/NqUXqcrwKlxiWTAQ.YFmSMvrwS",
            ("x".repeat(72) + "different") to "\$2b\$10\$ZbMA.1/XMIycG6v5xN/J5.kWXi8F5enjB4WNejTwBfptzUss3zKla")
        vectors.forEach { (input, expected) -> assertTrue(bcrypt.verifies(input, expected)) }
        val hash = bcrypt.hash("x".repeat(72))
        assertTrue(hash.startsWith("\$2b\$10\$")); assertTrue(bcrypt.verifies("x".repeat(72) + "suffix", hash))
        assertFalse(bcrypt.verifies("X" + "x".repeat(71), hash))
        assertFalse(bcrypt.verifies("wrong", vectors[0].second))
        assertFalse(bcrypt.verifies("wrong", "malformed"))
    }
}
