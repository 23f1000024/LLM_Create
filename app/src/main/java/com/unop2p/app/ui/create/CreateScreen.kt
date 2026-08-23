package com.unop2p.app.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unop2p.engine.game.GameSettings

@Composable
fun CreateScreen(
    nickname: String,
    onNickname: (String) -> Unit,
    onCreate: (maxPlayers: Int, settings: GameSettings) -> Unit,
    onBack: () -> Unit,
) {
    var maxPlayers by remember { mutableIntStateOf(4) }
    var stacking by remember { mutableStateOf(false) }
    var wd4Challenge by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Create Game", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = nickname, onValueChange = onNickname, label = { Text("Your nickname") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Text("Players (voice mesh works best with 2–4)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..4).forEach { n ->
                FilterChip(selected = maxPlayers == n, onClick = { maxPlayers = n }, label = { Text("$n") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = stacking, onCheckedChange = { stacking = it })
            Text("  Allow stacking +2 / +4", fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = wd4Challenge, onCheckedChange = { wd4Challenge = it })
            Text("  Wild Draw Four challenge", fontSize = 14.sp)
        }
        Button(
            onClick = {
                onCreate(maxPlayers, GameSettings(allowStacking = stacking, enableDrawFourChallenge = wd4Challenge))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create & Host") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
