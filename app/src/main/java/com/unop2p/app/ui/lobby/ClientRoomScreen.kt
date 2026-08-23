package com.unop2p.app.ui.lobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unop2p.app.session.ClientController
import com.unop2p.app.session.ConnectionStatus
import com.unop2p.app.ui.components.GameTable
import com.unop2p.app.ui.theme.UnoRed
import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.GamePhase

@Composable
fun ClientRoomScreen(client: ClientController, onLeave: () -> Unit, onDebug: () -> Unit) {
    val status by client.status.collectAsState()
    val state by client.gameState.collectAsState()
    val lobby by client.lobby.collectAsState()
    val error by client.error.collectAsState()

    val gs = state
    if (gs != null && gs.phase != GamePhase.LOBBY) {
        Column(Modifier.fillMaxSize()) {
            GameTable(
                state = gs,
                viewerId = client.localPlayerId,
                onPlay = { uid -> client.play(uid) },
                onDraw = { client.draw() },
                onCallUno = { client.callUno() },
                onChooseColor = { c: CardColor -> client.chooseColor(c) },
                onChallengeDrawFour = { client.challengeDrawFour() },
                modifier = Modifier.weight(1f),
                voiceBar = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { client.setMicEnabled(true) }) { Text("🎙 Unmute") }
                        OutlinedButton(onClick = { client.setMicEnabled(false) }) { Text("🔇 Mute") }
                        OutlinedButton(onClick = onDebug) { Text("Debug") }
                        OutlinedButton(onClick = onLeave) { Text("Leave") }
                    }
                },
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Joining…", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                when (status) {
                    ConnectionStatus.JOINING -> "Contacting host…"
                    ConnectionStatus.NEGOTIATING -> "Establishing direct P2P connection…"
                    ConnectionStatus.CONNECTED -> "Connected — waiting for the host to start."
                    ConnectionStatus.FAILED -> "Could not connect. The host may be unreachable on this network."
                    ConnectionStatus.ENDED -> "Session ended."
                    ConnectionStatus.IDLE -> "…"
                },
            )
            error?.let { Text(it, color = UnoRed, fontSize = 13.sp) }
            lobby?.let { l ->
                Text("Players", fontWeight = FontWeight.Bold)
                l.players.forEach { Text("• ${it.displayName}${if (it.ready) " ✓" else ""}") }
            }
            OutlinedButton(onClick = onDebug, modifier = Modifier.fillMaxWidth()) { Text("Network Debug") }
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Leave") }
        }
    }
}
