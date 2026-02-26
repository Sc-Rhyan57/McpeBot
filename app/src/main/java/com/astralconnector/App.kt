package com.astralconnector

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class AstralApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }
}
