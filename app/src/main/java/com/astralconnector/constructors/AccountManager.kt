package com.astralconnector.constructors

import android.content.Context
import com.astralconnector.auth.XboxDeviceInfo
import org.json.JSONArray
import org.json.JSONObject

object AccountManager {

    val accounts = mutableListOf<Account>()
    var selectedAccount: Account? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        load()
    }

    fun selectAccount(account: Account) {
        selectedAccount = account
        appContext?.getSharedPreferences("astral_prefs", Context.MODE_PRIVATE)
            ?.edit()?.putString("selected_account", account.remark)?.apply()
    }

    fun save() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        for (acc in accounts) {
            arr.put(JSONObject().apply {
                put("remark", acc.remark)
                put("refreshToken", acc.refreshToken)
                put("appId", acc.deviceInfo.appId)
                put("titleId", acc.deviceInfo.titleId)
                put("deviceType", acc.deviceInfo.deviceType)
                put("deviceVersion", acc.deviceInfo.deviceVersion)
            })
        }
        ctx.getSharedPreferences("astral_prefs", Context.MODE_PRIVATE)
            .edit().putString("accounts", arr.toString()).apply()
    }

    private fun load() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("astral_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("accounts", null) ?: return
        val selectedRemark = prefs.getString("selected_account", null)
        accounts.clear()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val acc = Account(
                remark = obj.getString("remark"),
                deviceInfo = XboxDeviceInfo(
                    appId = obj.getString("appId"),
                    titleId = obj.optString("titleId", ""),
                    deviceType = obj.optString("deviceType", "Android"),
                    deviceVersion = obj.optString("deviceVersion", "8.1.0"),
                ),
                refreshToken = obj.getString("refreshToken"),
            )
            accounts.add(acc)
            if (acc.remark == selectedRemark) selectedAccount = acc
        }
        if (selectedAccount == null && accounts.isNotEmpty()) selectedAccount = accounts[0]
    }

    fun removeAccount(account: Account) {
        accounts.remove(account)
        if (selectedAccount == account) selectedAccount = accounts.firstOrNull()
        save()
    }
}
