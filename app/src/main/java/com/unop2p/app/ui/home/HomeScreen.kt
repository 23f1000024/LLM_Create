package com.unop2p.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unop2p.app.ui.theme.UnoRed

@Composable
fun HomeScreen(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onLocal: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("UNO", color = UnoRed, fontSize = 64.sp, fontWeight = FontWeight.Black)
        Text("Peer-to-peer • no servers", fontSize = 14.sp)
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create Game") }
        Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text("Join Game") }
        OutlinedButton(onClick = onLocal, modifier = Modifier.fillMaxWidth()) { Text("Local (offline) Game") }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
    }
}
