package com.unop2p.app.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unop2p.app.qr.JoinTarget
import com.unop2p.app.qr.QrCodes
import com.unop2p.app.ui.theme.UnoRed

@Composable
fun JoinScreen(
    nickname: String,
    onNickname: (String) -> Unit,
    onScan: () -> Unit,
    onJoin: (JoinTarget) -> Unit,
    onBack: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Join Game", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(nickname, onNickname, label = { Text("Nickname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan QR Code") }
        Text("— or enter manually —", fontSize = 12.sp)
        OutlinedTextField(address, { address = it }, label = { Text("Host address (e.g. 192.168.1.42:8080)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(room, { room = it.uppercase() }, label = { Text("Room code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = UnoRed, fontSize = 13.sp) }
        OutlinedButton(
            onClick = {
                val target = QrCodes.parseManual(address, room)
                if (target == null) error = "Enter a valid address and room code." else onJoin(target)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Join") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
