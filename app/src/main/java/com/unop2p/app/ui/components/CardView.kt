package com.unop2p.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unop2p.app.ui.theme.UnoBlue
import com.unop2p.app.ui.theme.UnoGreen
import com.unop2p.app.ui.theme.UnoRed
import com.unop2p.app.ui.theme.UnoYellow
import com.unop2p.engine.game.Card
import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.CardKind

fun CardColor.toComposeColor(): Color = when (this) {
    CardColor.RED -> UnoRed
    CardColor.YELLOW -> UnoYellow
    CardColor.GREEN -> UnoGreen
    CardColor.BLUE -> UnoBlue
    CardColor.WILD -> Color(0xFF212121)
}

private fun Card.face(): String = when (kind) {
    CardKind.NUMBER -> number.toString()
    CardKind.SKIP -> "⊘"
    CardKind.REVERSE -> "⇄"
    CardKind.DRAW_TWO -> "+2"
    CardKind.WILD -> "W"
    CardKind.WILD_DRAW_FOUR -> "+4"
}

/** A single UNO card face. [enabled] highlights playable cards. */
@Composable
fun CardView(
    card: Card,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val base = card.color.toComposeColor()
    val bg = if (card.kind.isWild) {
        Brush.linearGradient(listOf(UnoRed, UnoYellow, UnoGreen, UnoBlue))
    } else {
        Brush.linearGradient(listOf(base, base))
    }
    Box(
        modifier = modifier
            .size(width = 58.dp, height = 86.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(2.dp, if (enabled) Color.White else Color(0x55FFFFFF), RoundedCornerShape(10.dp))
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = card.face(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (card.kind == CardKind.NUMBER) 30.sp else 22.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** A face-down card back (for opponents' hands / draw pile). */
@Composable
fun CardBack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 58.dp, height = 86.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF212121))
            .border(2.dp, Color(0xFF3A3F46), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("UNO", color = UnoRed, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}
