package com.astralconnector.auth

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class XboxDeviceInfo(
    val appId: String,
    val titleId: String,
    val deviceType: String,
    val deviceVersion: String,
    val sandbox: String = "RETAIL",
    val relyingParty: String = "https://multiplayer.minecraft.net/",
    val scope: String = "service::user.auth.xboxlive.com::MBI_SSL",
) {
    companion object {
        val NINTENDO_SWITCH = XboxDeviceInfo(
            appId = "00000000441cc96b",
            titleId = "2047319603",
            deviceType = "Nintendo",
            deviceVersion = "8.0.0",
        )
        val XBOX_ONE = XboxDeviceInfo(
            appId = "000000004C17C01A",
            titleId = "1739947436",
            deviceType = "XboxOne",
            deviceVersion = "10.0.0",
        )
        val ANDROID = XboxDeviceInfo(
            appId = "000000004C17C01A",
            titleId = "1810924247",
            deviceType = "Android",
            deviceVersion = "8.1.0",
        )

        val devices: Map<String, XboxDeviceInfo> = mapOf(
            "Nintendo" to NINTENDO_SWITCH,
            "XboxOne" to XBOX_ONE,
            "Android" to ANDROID,
        )
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun refreshToken(code: String, isAuthCode: Boolean): Pair<String, String> {
        val body = FormBody.Builder()
            .add("client_id", appId)
            .add("grant_type", if (isAuthCode) "authorization_code" else "refresh_token")
            .apply {
                if (isAuthCode) add("code", code)
                else add("refresh_token", code)
            }
            .add("redirect_uri", "https://login.live.com/oauth20_desktop.srf")
            .add("scope", scope)
            .build()

        val response = http.newCall(
            Request.Builder()
                .url("https://login.live.com/oauth20_token.srf")
                .post(body)
                .header("Accept", "application/json")
                .build()
        ).execute()

        val text = response.body?.string() ?: throw Exception("Empty token response")
        if (!response.isSuccessful) throw Exception("Token exchange failed ${response.code}: $text")

        val json = JSONObject(text)
        if (json.has("error")) throw Exception("Token error: ${json.optString("error_description", json.getString("error"))}")

        return json.getString("access_token") to json.getString("refresh_token")
    }
}
