package com.unop2p.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unop2p.app.common.AppLog
import com.unop2p.app.net.PeerLinkState

@Composable
fun DebugScreen(
    roomCode: String?,
    address: String?,
    peerStates: Map<String, PeerLinkState>,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Network Debug", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        roomCode?.let { Text("ROOM: $it") }
        address?.let { Text("LOCAL ADDRESS: $it") }
        Divider()
        Text("PEERS", fontWeight = FontWeight.Bold)
        if (peerStates.isEmpty()) Text("(no peers yet)")
        peerStates.values.forEach { p ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(p.playerId, fontWeight = FontWeight.Bold)
                Text("WebRTC: ${p.ice}")
                Text("Data:   ${if (p.dataOpen) "CONNECTED" else "…"}")
                Text("Voice:  ${if (p.hasRemoteAudio) "CONNECTED" else "WAITING"}")
            }
        }
        Divider()
        Text("LOGS", fontWeight = FontWeight.Bold)
        AppLog.recent().takeLast(120).forEach {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
