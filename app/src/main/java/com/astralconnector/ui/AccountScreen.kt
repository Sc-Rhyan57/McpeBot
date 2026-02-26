package com.astralconnector.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.astralconnector.ProxyViewModel
import com.astralconnector.model.XboxAccount

enum class AuthState { IDLE, ERROR }

@Composable
fun AccountScreen(vm: ProxyViewModel, onLaunchLogin: () -> Unit) {
    val account by vm.xboxAccount.collectAsState()
    val authState by vm.authState.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AC.GradTop, AC.GradBot)))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Account", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = AC.White)

        if (account != null) {
            AccountCard(account = account!!, onLogout = { vm.logoutXbox() })
        } else {
            LoginCard(isError = authState == AuthState.ERROR, onLogin = onLaunchLogin)
        }
    }
}

@Composable
private fun AccountCard(account: XboxAccount, onLogout: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AC.Card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, AC.Border, RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).background(AC.Primary.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(account.gamertag.firstOrNull()?.uppercase() ?: "X", color = AC.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(account.gamertag, color = AC.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Xbox Live", color = AC.Accent, fontSize = 12.sp)
                }
                Box(
                    Modifier.background(AC.Success.copy(0.1f), RoundedCornerShape(8.dp))
                        .border(1.dp, AC.Success.copy(0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("Connected", color = AC.Success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = AC.Border)

            if (account.xuid.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Badge, null, tint = AC.Muted, modifier = Modifier.size(14.dp))
                    Text("XUID: ${account.xuid}", color = AC.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = AC.Error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LoginCard(isError: Boolean, onLogin: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AC.Card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, if (isError) AC.Error.copy(0.5f) else AC.Border, RoundedCornerShape(16.dp))
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Outlined.AccountCircle, null, tint = AC.Muted, modifier = Modifier.size(56.dp))
            Text("No Xbox account", color = AC.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Sign in with your Microsoft account to access Xbox Live features",
                color = AC.Muted, fontSize = 12.sp, textAlign = TextAlign.Center
            )
            if (isError) {
                Text("Login failed. Please try again.", color = AC.Error, fontSize = 12.sp)
            }
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(containerColor = AC.Primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.AccountCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign in with Microsoft", fontWeight = FontWeight.Bold)
            }
        }
    }
}
