package com.astralconnector.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.util.concurrent.TimeUnit

class XboxGamerTagException(val sisuStartUrl: String) : Exception("No gamertag found")

data class IdentityToken(val token: String)

data class XboxIdentityToken(val token: String, val notAfter: Long)

interface IXboxIdentityTokenCache {
    fun checkCache(deviceInfo: XboxDeviceInfo): XboxIdentityToken?
    fun cache(deviceInfo: XboxDeviceInfo, token: XboxIdentityToken)
}

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

private val http = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

fun base64Decode(s: String): ByteArray {
    val padded = when (s.length % 4) {
        2 -> "$s=="
        3 -> "$s="
        else -> s
    }
    return android.util.Base64.decode(
        padded.replace('-', '+').replace('_', '/'),
        android.util.Base64.DEFAULT
    )
}

fun fetchIdentityToken(accessToken: String, deviceInfo: XboxDeviceInfo): XboxIdentityToken {
    val rpsTicket = when {
        accessToken.startsWith("d=") || accessToken.startsWith("t=") -> accessToken
        deviceInfo.appId == "000000004C17C01A" -> "t=$accessToken"
        else -> "d=$accessToken"
    }

    val userTokenJson = JSONObject().apply {
        put("RelyingParty", "http://auth.xboxlive.com")
        put("TokenType", "JWT")
        put("Properties", JSONObject().apply {
            put("AuthMethod", "RPS")
            put("SiteName", "user.auth.xboxlive.com")
            put("RpsTicket", rpsTicket)
        })
    }.toString()

    val userResp = http.newCall(
        Request.Builder()
            .url("https://user.auth.xboxlive.com/user/authenticate")
            .post(userTokenJson.toRequestBody(JSON_TYPE))
            .header("Accept", "application/json")
            .header("x-xbl-contract-version", "0")
            .build()
    ).execute()

    val userText = userResp.body?.string() ?: throw Exception("Empty user auth response")
    if (!userResp.isSuccessful) throw Exception("User auth failed ${userResp.code}: $userText")
    val userJson = JSONObject(userText)
    val userToken = userJson.getString("Token")

    val xstsJson = JSONObject().apply {
        put("RelyingParty", deviceInfo.relyingParty)
        put("TokenType", "JWT")
        put("Properties", JSONObject().apply {
            put("SandboxId", deviceInfo.sandbox)
            put("UserTokens", JSONArray().apply { put(userToken) })
        })
    }.toString()

    val xstsResp = http.newCall(
        Request.Builder()
            .url("https://xsts.auth.xboxlive.com/xsts/authorize")
            .post(xstsJson.toRequestBody(JSON_TYPE))
            .header("Accept", "application/json")
            .header("x-xbl-contract-version", "1")
            .build()
    ).execute()

    val xstsText = xstsResp.body?.string() ?: throw Exception("Empty XSTS response")

    if (xstsResp.code == 401) {
        val errJson = JSONObject(xstsText)
        val xerr = errJson.optLong("XErr")
        if (xerr == 2148916235L || xerr == 2148916236L || xerr == 2148916238L) {
            val redirect = errJson.optString("Redirect", "")
            if (redirect.isNotEmpty()) throw XboxGamerTagException(redirect)
        }
        throw Exception("XSTS auth 401: $xstsText")
    }

    if (!xstsResp.isSuccessful) throw Exception("XSTS auth failed ${xstsResp.code}: $xstsText")

    val xstsRespJson = JSONObject(xstsText)
    val xstsToken = xstsRespJson.getString("Token")
    val xstsUhs = xstsRespJson.getJSONObject("DisplayClaims")
        .getJSONArray("xui").getJSONObject(0).getString("uhs")

    val notAfterStr = xstsRespJson.optString("NotAfter", "")
    val notAfter = if (notAfterStr.isNotEmpty()) {
        runCatching {
            java.time.Instant.parse(notAfterStr).epochSecond
        }.getOrElse { System.currentTimeMillis() / 1000 + 86400 }
    } else {
        System.currentTimeMillis() / 1000 + 86400
    }

    return XboxIdentityToken(
        token = "XBL3.0 x=$xstsUhs;$xstsToken",
        notAfter = notAfter
    )
}

fun fetchChain(identityToken: String, keyPair: KeyPair): List<String> {
    val ecKey = keyPair.public as ECPublicKey

    fun trimLeadingZero(b: ByteArray): ByteArray =
        if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b

    fun encodeBase64Url(b: ByteArray): String =
        android.util.Base64.encodeToString(
            b,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )

    val x = encodeBase64Url(trimLeadingZero(ecKey.w.affineX.toByteArray()))
    val y = encodeBase64Url(trimLeadingZero(ecKey.w.affineY.toByteArray()))

    val jwkString = JSONObject().apply {
        put("crv", "P-384")
        put("kty", "EC")
        put("x", x)
        put("y", y)
    }.toString()

    val payload = JSONObject().apply {
        put("identityPublicKey", jwkString)
    }.toString()

    val resp = http.newCall(
        Request.Builder()
            .url("https://multiplayer.minecraft.net/authentication")
            .post(payload.toRequestBody(JSON_TYPE))
            .header("Authorization", identityToken)
            .header("Content-Type", "application/json")
            .header("Client-Version", "1.21.50")
            .header("User-Agent", "MCPE/Android")
            .build()
    ).execute()

    val text = resp.body?.string() ?: throw Exception("Empty chain response")
    if (!resp.isSuccessful) throw Exception("Chain fetch failed ${resp.code}: $text")

    val chainArray = JSONObject(text).getJSONArray("chain")
    return (0 until chainArray.length()).map { chainArray.getString(it) }
}

fun fetchRawChain(identityToken: String, publicKey: java.security.PublicKey): InputStream {
    val ecKey = publicKey as ECPublicKey

    fun trimLeadingZero(b: ByteArray): ByteArray =
        if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b

    fun encodeBase64Url(b: ByteArray): String =
        android.util.Base64.encodeToString(
            b,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )

    val x = encodeBase64Url(trimLeadingZero(ecKey.w.affineX.toByteArray()))
    val y = encodeBase64Url(trimLeadingZero(ecKey.w.affineY.toByteArray()))

    val jwkString = JSONObject().apply {
        put("crv", "P-384")
        put("kty", "EC")
        put("x", x)
        put("y", y)
    }.toString()

    val payload = JSONObject().apply {
        put("identityPublicKey", jwkString)
    }.toString()

    val resp = http.newCall(
        Request.Builder()
            .url("https://multiplayer.minecraft.net/authentication")
            .post(payload.toRequestBody(JSON_TYPE))
            .header("Authorization", identityToken)
            .header("Content-Type", "application/json")
            .header("Client-Version", "1.21.50")
            .header("User-Agent", "MCPE/Android")
            .build()
    ).execute()

    val text = resp.body?.string() ?: throw Exception("Empty chain response")
    if (!resp.isSuccessful) throw Exception("Chain fetch failed ${resp.code}: $text")
    return text.byteInputStream()
}
