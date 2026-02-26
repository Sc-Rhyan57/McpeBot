package com.astralconnector.constructors

import com.astralconnector.auth.XboxDeviceInfo

data class Account(
    var remark: String,
    val deviceInfo: XboxDeviceInfo,
    val refreshToken: String,
)
