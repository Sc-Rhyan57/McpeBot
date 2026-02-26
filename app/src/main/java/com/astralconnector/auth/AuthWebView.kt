package com.astralconnector.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.astralconnector.constructors.Account
import com.astralconnector.constructors.AccountManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.random.nextInt

@SuppressLint("SetJavaScriptEnabled")
class AuthWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : WebView(context, attrs) {

    private var sisuAccount: Pair<String, String>? = null
    private val handler = Handler(Looper.getMainLooper())

    var deviceInfo: XboxDeviceInfo? = null
    var callback: ((success: Boolean) -> Unit)? = null

    init {
        CookieManager.getInstance().removeAllCookies(null)
        settings.javaScriptEnabled = true
        webViewClient = AuthWebViewClient()
    }

    fun addAccount() {
        loadUrl(
            "https://login.live.com/oauth20_authorize.srf" +
                    "?client_id=${deviceInfo!!.appId}" +
                    "&redirect_uri=https://login.live.com/oauth20_desktop.srf" +
                    "&response_type=code" +
                    "&scope=${deviceInfo!!.scope}" +
                    "&display=touch" +
                    "&locale=en"
        )
    }

    inner class AuthWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            if (sisuAccount != null && (request.url.scheme ?: "").startsWith("ms-xal")) {
                thread {
                    try {
                        handler.post { showLoadingPage("Verifying your credentials...") }
                        val kpg = KeyPairGenerator.getInstance("EC").apply {
                            initialize(ECGenParameterSpec("secp384r1"))
                        }
                        val identityToken = fetchIdentityToken(sisuAccount!!.first, deviceInfo!!)
                        handler.post { showLoadingPage("Almost done...") }
                        val username = getUsernameFromChain(
                            fetchRawChain(identityToken.token, kpg.generateKeyPair().public)
                                .bufferedReader().readText()
                        )
                        val newAccount = Account(username, deviceInfo!!, sisuAccount!!.second)
                        AccountManager.accounts.add(newAccount)
                        AccountManager.save()
                        AccountManager.selectAccount(newAccount)
                        handler.post { callback?.invoke(true) }
                    } catch (t: Throwable) {
                        Log.e("AuthWebView", "ms-xal auth: ${t.stackTraceToString()}")
                        handler.post { loadErrorPage(t.stackTraceToString()) }
                    }
                }
                return true
            }

            val url = request.url.toString().toHttpUrlOrNull() ?: return false

            if (url.host != "login.live.com" || url.encodedPath != "/oauth20_desktop.srf") {
                if (url.queryParameter("res") == "cancel") {
                    Log.e("AuthWebView", "Action cancelled by user")
                    handler.post { callback?.invoke(false) }
                    return false
                }
                return false
            }

            val authCode = url.queryParameter("code") ?: return false

            handler.post { showLoadingPage("Setting up your account...") }

            thread {
                try {
                    val (accessToken, refreshToken) = deviceInfo!!.refreshToken(authCode, isAuthCode = true)
                    handler.post { showLoadingPage("Authenticating with Xbox...") }

                    val username = try {
                        val identityToken = fetchIdentityToken(accessToken, deviceInfo!!)
                        handler.post { showLoadingPage("Retrieving your profile...") }
                        val kpg2 = KeyPairGenerator.getInstance("EC").apply {
                            initialize(ECGenParameterSpec("secp384r1"))
                        }
                        getUsernameFromChain(
                            fetchRawChain(identityToken.token, kpg2.generateKeyPair().public)
                                .bufferedReader().readText()
                        )
                    } catch (e: XboxGamerTagException) {
                        sisuAccount = accessToken to refreshToken
                        handler.post { loadUrl(e.sisuStartUrl) }
                        return@thread
                    }

                    val account = Account(username, deviceInfo!!, refreshToken)
                    while (AccountManager.accounts.map { it.remark }.contains(account.remark)) {
                        account.remark += Random.nextInt(0..9)
                    }
                    AccountManager.accounts.add(account)
                    AccountManager.save()
                    AccountManager.selectAccount(account)
                    handler.post { callback?.invoke(true) }
                } catch (t: Throwable) {
                    Log.e("AuthWebView", "Auth failed: ${t.stackTraceToString()}")
                    handler.post { loadErrorPage(t.stackTraceToString()) }
                }
            }
            return true
        }
    }

    private fun getUsernameFromChain(chains: String): String {
        val body = org.json.JSONObject(chains).getJSONArray("chain")
        for (i in 0 until body.length()) {
            val raw = body.getString(i)
            val parts = raw.split(".")
            if (parts.size < 2) continue
            val decoded = base64Decode(parts[1]).toString(Charsets.UTF_8)
            val chainBody = org.json.JSONObject(decoded)
            if (chainBody.has("extraData")) {
                return chainBody.getJSONObject("extraData").getString("displayName")
            }
        }
        error("no username found in chain")
    }

    fun showLoadingPage(title: String) {
        val html = "<html><body style='background:#1a1a2e;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0'>" +
                "<div style='text-align:center'><div style='font-size:24px;margin-bottom:16px'>⏳</div><div>$title</div></div></body></html>"
        val encoded = Base64.encodeToString(html.toByteArray(), Base64.DEFAULT)
        loadData(encoded, "text/html; charset=UTF-8", "base64")
    }

    fun loadErrorPage(text: String) {
        loadData(text, "text/plain", "UTF-8")
    }
}
