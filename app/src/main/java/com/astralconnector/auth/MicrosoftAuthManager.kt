package com.astralconnector.auth

import com.astralconnector.model.XboxAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object MicrosoftAuthManager {

    private const val CLIENT_ID = "0000000048093EE3"
    private const val REDIRECT = "https://login.live.com/oauth20_desktop.srf"
    private const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
    private const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"

    val LOGIN_URL = "https://login.live.com/oauth20_authorize.srf" +
            "?client_id=$CLIENT_ID" +
            "&redirect_uri=$REDIRECT" +
            "&response_type=code" +
            "&scope=$SCOPE" +
            "&display=touch" +
            "&locale=en"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    suspend fun finishLogin(code: String): XboxAccount = withContext(Dispatchers.IO) {
        val oauthToken = exchangeCodeForToken(code)
        val accessToken = oauthToken.getString("access_token")

        val userToken = fetchUserToken(accessToken)
        val xstsJson = fetchXSTS(userToken)
        val token = xstsJson.getString("Token")
        val xui = xstsJson.getJSONObject("DisplayClaims")
            .getJSONArray("xui").getJSONObject(0)
        val uhs = xui.getString("uhs")
        val gamertag = xui.optString("gtg", "Player")
        val xuid = xui.optString("xid", "")
        val mcToken = fetchMinecraftToken(uhs, token)

        XboxAccount(
            gamertag = gamertag,
            xuid = xuid,
            accessToken = mcToken,
            userHash = uhs,
            identityToken = "XBL3.0 x=$uhs;$token",
            expiresAt = System.currentTimeMillis() + 86400_000L
        )
    }

    private fun exchangeCodeForToken(code: String): JSONObject {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT)
            .add("scope", SCOPE)
            .build()

        val resp = http.newCall(
            Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .header("Accept", "application/json")
                .build()
        ).execute()

        val text = resp.body?.string() ?: throw Exception("Empty token exchange response")
        if (!resp.isSuccessful) throw Exception("Token exchange failed ${resp.code}: $text")
        val json = JSONObject(text)
        if (!json.has("access_token")) throw Exception("No access_token in response: $text")
        return json
    }

    private fun fetchUserToken(accessToken: String): String {
        val body = JSONObject().apply {
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
            put("Properties", JSONObject().apply {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", accessToken)
            })
        }.toString().toRequestBody(JSON_MEDIA)

        val resp = http.newCall(
            Request.Builder()
                .url("https://user.auth.xboxlive.com/user/authenticate")
                .post(body)
                .header("Accept", "application/json")
                .build()
        ).execute()

        val text = resp.body?.string() ?: throw Exception("Empty user token response")
        if (!resp.isSuccessful) throw Exception("UserToken failed ${resp.code}: $text")
        return JSONObject(text).getString("Token")
    }

    private fun fetchXSTS(userToken: String): JSONObject {
        val body = JSONObject().apply {
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
            put("Properties", JSONObject().apply {
                put("SandboxId", "RETAIL")
                put("UserTokens", JSONArray().apply { put(userToken) })
            })
        }.toString().toRequestBody(JSON_MEDIA)

        val resp = http.newCall(
            Request.Builder()
                .url("https://xsts.auth.xboxlive.com/xsts/authorize")
                .post(body)
                .header("Accept", "application/json")
                .build()
        ).execute()

        val text = resp.body?.string() ?: throw Exception("Empty XSTS response")
        if (!resp.isSuccessful) throw Exception("XSTS failed ${resp.code}: $text")
        return JSONObject(text)
    }

    private fun fetchMinecraftToken(uhs: String, xstsToken: String): String {
        val body = JSONObject().apply {
            put("identityToken", "XBL3.0 x=$uhs;$xstsToken")
        }.toString().toRequestBody(JSON_MEDIA)

        val resp = http.newCall(
            Request.Builder()
                .url("https://api.minecraftservices.com/authentication/login_with_xbox")
                .post(body)
                .header("Accept", "application/json")
                .build()
        ).execute()

        val text = resp.body?.string() ?: throw Exception("Empty MC token response")
        if (!resp.isSuccessful) throw Exception("MC token failed ${resp.code}: $text")
        return JSONObject(text).getString("access_token")
    }
}
