package com.unop2p.app.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unop2p.app.session.LocalGameController
import com.unop2p.app.ui.components.GameTable
import com.unop2p.engine.game.CardColor

@Composable
fun LocalSetupScreen(onStart: (List<String>) -> Unit, onBack: () -> Unit) {
    var count by remember { mutableIntStateOf(2) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Local Game", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Pass-and-play on one device — great for testing the rules with no networking.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..4).forEach { n -> FilterChip(count == n, { count = n }, { Text("$n players") }) }
        }
        Button(
            onClick = { onStart((1..count).map { "Player $it" }) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun LocalGameScreen(controller: LocalGameController, onExit: () -> Unit) {
    val state by controller.state.collectAsState()
    val viewerId = state.currentPlayer?.playerId ?: state.players.first().playerId
    val viewerName = state.currentPlayer?.displayName ?: ""

    Column(Modifier.fillMaxSize()) {
        Text(
            "  Pass to: $viewerName",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp),
        )
        GameTable(
            state = state.redactedFor(viewerId),
            viewerId = viewerId,
            onPlay = { uid -> controller.play(uid) },
            onDraw = { controller.draw() },
            onCallUno = { controller.callUno() },
            onChooseColor = { c: CardColor -> controller.chooseColor(c) },
            modifier = Modifier.weight(1f),
            voiceBar = { OutlinedButton(onClick = onExit) { Text("Exit") } },
        )
    }
}
