package com.unop2p.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    nickname: String,
    onNickname: (String) -> Unit,
    stunEnabled: Boolean,
    onStunEnabled: (Boolean) -> Unit,
    turnUrl: String,
    turnUser: String,
    turnCred: String,
    onTurn: (url: String, user: String, cred: String) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf(turnUrl) }
    var user by remember { mutableStateOf(turnUser) }
    var cred by remember { mutableStateOf(turnCred) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = nickname, onValueChange = onNickname, label = { Text("Nickname") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Only your nickname and these preferences are stored on this device. " +
                "No game data ever leaves your phone except directly to other players.",
            fontSize = 12.sp,
        )

        Divider()
        Text("Internet connectivity (advanced)", fontWeight = FontWeight.Bold)
        Text(
            "Same-Wi-Fi play needs none of this. For Internet play the host must be " +
                "reachable (router port-forward or public IPv6), and NAT traversal needs " +
                "STUN — and often a TURN relay you provide.",
            fontSize = 12.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = stunEnabled, onCheckedChange = onStunEnabled)
            Text("  Use STUN (public address discovery)", fontSize = 14.sp)
        }
        Text("TURN relay (optional — enables Internet play through hostile NAT):", fontSize = 12.sp)
        OutlinedTextField(url, { url = it }, label = { Text("TURN URL (turn:host:3478)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(user, { user = it }, label = { Text("TURN username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cred, { cred = it }, label = { Text("TURN credential") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onTurn(url, user, cred) }, modifier = Modifier.fillMaxWidth()) { Text("Save TURN settings") }

        TextButton(onClick = onBack) { Text("Back") }
    }
}
