package com.nikac.guider.data.auth

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.FirebaseOptions

data class FirebaseConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val webClientId: String,
) {
    // Check shape only; the provider validates that these identifiers belong together.
    val isConfigured: Boolean
        get() = apiKey.matches(Regex("AIza[\\w-]{35}")) &&
            appId.matches(Regex("1:[0-9]+:android:[a-fA-F0-9]+")) &&
            projectId.isNotBlank() && projectId.none(Char::isWhitespace) &&
            webClientId.endsWith(".apps.googleusercontent.com") &&
            webClientId.substringBefore(".apps.googleusercontent.com").isNotBlank() &&
            webClientId.none(Char::isWhitespace)

    companion object {
        fun fromResources(context: Context): FirebaseConfig {
            val options = FirebaseOptions.fromResource(context)
            return FirebaseConfig(
                apiKey = options?.apiKey.orEmpty(),
                appId = options?.applicationId.orEmpty(),
                projectId = options?.projectId.orEmpty(),
                webClientId = context.generatedString("default_web_client_id"),
            )
        }
    }
}

// The generated resource does not exist until a valid google-services.json is present.
@SuppressLint("DiscouragedApi")
private fun Context.generatedString(name: String): String {
    val id = resources.getIdentifier(name, "string", packageName)
    return if (id == 0) "" else getString(id).trim()
}
