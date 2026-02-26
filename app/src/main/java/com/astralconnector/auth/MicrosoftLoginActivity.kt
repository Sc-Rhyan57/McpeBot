package com.astralconnector.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.astralconnector.constructors.AccountManager
import kotlin.concurrent.thread

class MicrosoftLoginActivity : ComponentActivity() {

    companion object {
        fun intent(context: Context) = Intent(context, MicrosoftLoginActivity::class.java)
    }

    private var webView: AuthWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AccountManager.init(this)

        webView = AuthWebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            deviceInfo = XboxDeviceInfo.ANDROID
            callback = { success ->
                if (success) {
                    val account = AccountManager.selectedAccount
                    if (account != null) {
                        runOnUiThread { webView?.showLoadingPage("Refreshing access token...") }
                        thread {
                            try {
                                val (accessToken, newRefreshToken) = account.deviceInfo.refreshToken(
                                    account.refreshToken,
                                    isAuthCode = false
                                )

                                runOnUiThread { webView?.showLoadingPage("Authenticating with Xbox...") }

                                val identityToken = fetchIdentityToken(accessToken, account.deviceInfo)

                                val gamertag = extractGamertagFromToken(identityToken.token)
                                    ?: account.remark

                                val resultIntent = Intent().apply {
                                    putExtra("gamertag", gamertag)
                                    putExtra("xuid", "")
                                    putExtra("accessToken", identityToken.token)
                                    putExtra("userHash", extractUhsFromToken(identityToken.token))
                                    putExtra("identityToken", identityToken.token)
                                    putExtra("expiresAt", identityToken.notAfter * 1000L)
                                }

                                runOnUiThread {
                                    setResult(Activity.RESULT_OK, resultIntent)
                                    finish()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    webView?.loadErrorPage("Failed to fetch token: ${e.message}\n\n${e.stackTraceToString()}")
                                }
                            }
                        }
                    } else {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }

        val frame = FrameLayout(this).apply {
            addView(webView)
        }
        setContentView(frame)

        webView?.addAccount()
    }

    private fun extractUhsFromToken(identityToken: String): String {
        return try {
            identityToken.substringAfter("x=").substringBefore(";")
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractGamertagFromToken(identityToken: String): String? {
        return try {
            val xstsToken = identityToken.substringAfter(";")
            val parts = xstsToken.split(".")
            if (parts.size < 2) return null
            val decoded = base64Decode(parts[1]).toString(Charsets.UTF_8)
            val json = org.json.JSONObject(decoded)
            val xui = json.optJSONObject("DisplayClaims")
                ?.optJSONArray("xui")
                ?.optJSONObject(0)
            xui?.optString("gtg")?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
