package com.nikac.guider.data.auth

import org.junit.Assert.*
import org.junit.Test

class FirebaseConfigTest {
    private val config = FirebaseConfig(
        apiKey = "AIza" + "x".repeat(35),
        appId = "1:123456789:android:abc123def456",
        projectId = "test-project",
        webClientId = "123456789-example.apps.googleusercontent.com",
    )

    @Test fun `valid client configuration enables sign in`() { assertTrue(config.isConfigured) }

    @Test fun `empty or partial configuration keeps sign in disabled`() {
        assertFalse(FirebaseConfig("", "", "", "").isConfigured)
        assertFalse(config.copy(apiKey = "").isConfigured)
        assertFalse(config.copy(appId = "").isConfigured)
        assertFalse(config.copy(projectId = "").isConfigured)
        assertFalse(config.copy(webClientId = "").isConfigured)
    }

    @Test fun `obvious placeholders and malformed identifiers are rejected`() {
        assertFalse(config.copy(apiKey = "YOUR_API_KEY").isConfigured)
        assertFalse(config.copy(appId = "com.nikac.guider").isConfigured)
        assertFalse(config.copy(webClientId = "YOUR_CLIENT_ID").isConfigured)
        assertFalse(config.copy(webClientId = ".apps.googleusercontent.com").isConfigured)
        assertFalse(config.copy(projectId = "invalid project").isConfigured)
    }
}
