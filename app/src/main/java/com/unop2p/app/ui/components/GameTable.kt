package com.unop2p.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.GamePhase
import com.unop2p.engine.game.GameRules
import com.unop2p.engine.game.PublicGameState

/**
 * The shared game table. Renders opponents, the discard/draw piles and the
 * viewer's own hand, and surfaces the legal actions. Used identically by the
 * host, joining clients and the offline hot-seat mode — only the callbacks differ.
 */
@Composable
fun GameTable(
    state: PublicGameState,
    viewerId: String,
    onPlay: (cardUid: String) -> Unit,
    onDraw: () -> Unit,
    onCallUno: () -> Unit,
    onChooseColor: (CardColor) -> Unit,
    modifier: Modifier = Modifier,
    onChallengeDrawFour: (() -> Unit)? = null,
    voiceBar: (@Composable () -> Unit)? = null,
) {
    val isViewerTurn = state.currentPlayerId == viewerId
    val viewer = state.players.firstOrNull { it.playerId == viewerId }
    val hand = viewer?.hand.orEmpty()
    val mustChooseColor = state.phase == GamePhase.AWAITING_COLOR && state.currentPlayerId == viewerId

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        // Opponents strip
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.players.filter { it.playerId != viewerId }.forEach { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val turnMark = if (p.playerId == state.currentPlayerId) "▶ " else ""
                    Text(
                        "$turnMark${p.displayName}",
                        fontWeight = if (p.playerId == state.currentPlayerId) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text("${p.handCount} cards", fontSize = 12.sp)
                    if (p.handCount == 1) Text("UNO!", color = com.unop2p.app.ui.theme.UnoRed, fontSize = 12.sp)
                    val conn = p.connectionState.name.lowercase()
                    Text(conn, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Draw", fontSize = 12.sp)
                    CardBack()
                    Text("${state.drawPileCount}", fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Discard", fontSize = 12.sp)
                    state.topCard?.let { CardView(it, enabled = true) }
                    state.activeColor?.let { Text("Color: ${it.name}", fontSize = 12.sp) }
                    if (state.pendingDraw > 0) Text("+${state.pendingDraw} pending", color = com.unop2p.app.ui.theme.UnoRed)
                }
            }
        }

        // Winner / turn banner
        when {
            state.phase == GamePhase.FINISHED -> {
                val winner = state.players.firstOrNull { it.playerId == state.winnerId }?.displayName ?: "?"
                Text("🏆 $winner wins!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            mustChooseColor -> Text("Choose a color", fontWeight = FontWeight.Bold)
            isViewerTurn -> Text("Your turn", fontWeight = FontWeight.Bold, color = com.unop2p.app.ui.theme.UnoGreen)
            else -> Text("Waiting for ${state.players.firstOrNull { it.playerId == state.currentPlayerId }?.displayName ?: ""}…")
        }

        Spacer(Modifier.width(8.dp))

        // Color picker
        if (mustChooseColor) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE).forEach { c ->
                    Button(onClick = { onChooseColor(c) }) { Text(c.name) }
                }
            }
        }

        // Action row
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDraw, enabled = isViewerTurn && !mustChooseColor && state.phase == GamePhase.PLAYING) {
                Text(if (state.pendingDraw > 0) "Draw +${state.pendingDraw}" else "Draw")
            }
            OutlinedButton(onClick = onCallUno, enabled = (viewer?.handCount ?: 0) <= 2) { Text("UNO") }
            if (onChallengeDrawFour != null && state.pendingDraw > 0 && isViewerTurn) {
                OutlinedButton(onClick = onChallengeDrawFour) { Text("Challenge") }
            }
        }

        voiceBar?.invoke()

        // Viewer's hand
        Spacer(Modifier.width(8.dp))
        Text("Your hand (${hand.size})", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            hand.forEach { card ->
                val playable = isViewerTurn && state.phase == GamePhase.PLAYING && GameRules.isPlayable(
                    card = card,
                    topCard = state.topCard,
                    activeColor = state.activeColor,
                    pendingDraw = state.pendingDraw,
                    pendingDrawKind = state.pendingDrawKind,
                    allowStacking = state.settings.allowStacking,
                )
                CardView(card = card, enabled = playable, onClick = { onPlay(card.uid) })
            }
        }
    }
}
