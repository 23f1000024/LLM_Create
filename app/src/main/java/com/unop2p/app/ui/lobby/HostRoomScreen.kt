package com.unop2p.app.ui.lobby

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unop2p.app.qr.QrCodes
import com.unop2p.app.session.HostController
import com.unop2p.app.ui.components.GameTable
import com.unop2p.app.ui.theme.UnoRed
import com.unop2p.engine.game.CardColor

@Composable
fun HostRoomScreen(host: HostController, onLeave: () -> Unit, onDebug: () -> Unit) {
    val lobby by host.lobby.collectAsState()
    val gameState by host.gameState.collectAsState()
    val micEnabled by host.micEnabled.collectAsState()

    val state = gameState
    if (state == null || state.phase == com.unop2p.engine.game.GamePhase.LOBBY) {
        // ---- Lobby ----
        val address = remember { host.bestAddress() }
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("UNO Lobby", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Room code: ${host.roomCode}", fontSize = 20.sp, color = UnoRed, fontWeight = FontWeight.Bold)
            if (address != null) {
                Text("Host address: ${address.hostPort(HostController.DEFAULT_PORT)}")
                val uri = remember(address) { host.joinUri(address) }
                val bmp = remember(uri) { QrCodes.generate(uri, 480) }
                Image(bmp.asImageBitmap(), contentDescription = "Join QR", modifier = Modifier.size(220.dp))
                Text("Scan to join, or type the address above.", fontSize = 12.sp)
            } else {
                Text("No usable network address found. Connect to Wi-Fi and reopen.", color = UnoRed)
            }
            Divider()
            Text("Players", fontWeight = FontWeight.Bold)
            lobby.forEach { p ->
                Text("• ${p.displayName}${if (p.connected) "" else " (connecting…)"}")
            }
            Button(
                onClick = { host.startGame() },
                enabled = lobby.size >= 2,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start Game") }
            OutlinedButton(onClick = onDebug, modifier = Modifier.fillMaxWidth()) { Text("Network Debug") }
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("End Room") }
        }
    } else {
        // ---- Game ----
        Column(Modifier.fillMaxSize()) {
            GameTable(
                state = state,
                viewerId = host.hostPlayerId,
                onPlay = { uid -> host.play(uid) },
                onDraw = { host.draw() },
                onCallUno = { host.callUno() },
                onChooseColor = { c: CardColor -> host.chooseColor(c) },
                onChallengeDrawFour = { host.challengeDrawFour() },
                modifier = Modifier.weight(1f),
                voiceBar = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { host.setMicEnabled(!micEnabled) }) {
                            Text(if (micEnabled) "🎙 Mic On" else "🔇 Mic Off")
                        }
                        OutlinedButton(onClick = onDebug) { Text("Debug") }
                        OutlinedButton(onClick = onLeave) { Text("End") }
                    }
                },
            )
        }
    }
}
