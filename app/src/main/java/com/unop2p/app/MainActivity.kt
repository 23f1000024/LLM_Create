package com.unop2p.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import com.unop2p.app.ui.AppViewModel
import com.unop2p.app.ui.Route
import com.unop2p.app.ui.create.CreateScreen
import com.unop2p.app.ui.debug.DebugScreen
import com.unop2p.app.ui.home.HomeScreen
import com.unop2p.app.ui.join.JoinScreen
import com.unop2p.app.ui.join.QrScannerScreen
import com.unop2p.app.ui.lobby.ClientRoomScreen
import com.unop2p.app.ui.lobby.HostRoomScreen
import com.unop2p.app.ui.local.LocalGameScreen
import com.unop2p.app.ui.local.LocalSetupScreen
import com.unop2p.app.ui.settings.SettingsScreen
import com.unop2p.app.ui.theme.UnoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: AppViewModel = viewModel()
                    AppNav(vm)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppNav(vm: AppViewModel) {
    // Request microphone permission up front so voice can start when the user unmutes.
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    BackHandler(enabled = vm.route != Route.Home) { if (!vm.back()) { /* at root */ } }

    when (vm.route) {
        Route.Home -> HomeScreen(
            onCreate = { vm.navigate(Route.Create) },
            onJoin = { vm.navigate(Route.JoinEntry) },
            onLocal = { vm.navigate(Route.LocalSetup) },
            onSettings = { vm.navigate(Route.Settings) },
        )
        Route.Create -> CreateScreen(
            nickname = vm.nickname,
            onNickname = vm::updateNickname,
            onCreate = { max, settings -> vm.createGame(max, settings) },
            onBack = { vm.back() },
        )
        Route.HostRoom -> vm.host?.let { host ->
            HostRoomScreen(host = host, onLeave = { vm.leaveHost() }, onDebug = { vm.navigate(Route.Debug) })
        } ?: HomeFallback(vm)
        Route.JoinEntry -> JoinScreen(
            nickname = vm.nickname,
            onNickname = vm::updateNickname,
            onScan = { vm.navigate(Route.Scan) },
            onJoin = { target -> vm.joinGame(target) },
            onBack = { vm.back() },
        )
        Route.Scan -> QrScannerScreen(
            onResult = { target -> vm.back(); vm.joinGame(target) },
            onBack = { vm.back() },
        )
        Route.ClientRoom -> vm.client?.let { client ->
            ClientRoomScreen(client = client, onLeave = { vm.leaveClient() }, onDebug = { vm.navigate(Route.Debug) })
        } ?: HomeFallback(vm)
        Route.LocalSetup -> LocalSetupScreen(onStart = { names -> vm.startLocal(names) }, onBack = { vm.back() })
        Route.LocalGame -> vm.local?.let { LocalGameScreen(controller = it, onExit = { vm.leaveLocal() }) } ?: HomeFallback(vm)
        Route.Settings -> SettingsScreen(
            nickname = vm.nickname,
            onNickname = vm::updateNickname,
            stunEnabled = vm.stunEnabled,
            onStunEnabled = vm::updateStunEnabled,
            turnUrl = vm.turnUrl,
            turnUser = vm.turnUser,
            turnCred = vm.turnCred,
            onTurn = vm::setTurn,
            onBack = { vm.back() },
        )
        Route.Debug -> {
            val host = vm.host
            val client = vm.client
            val peerStates = when {
                host != null -> host.peerStates.collectAsState().value
                client != null -> client.peerStates.collectAsState().value
                else -> emptyMap()
            }
            DebugScreen(
                roomCode = host?.roomCode,
                address = host?.bestAddress()?.hostPort(com.unop2p.app.session.HostController.DEFAULT_PORT),
                peerStates = peerStates,
                onBack = { vm.back() },
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun HomeFallback(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.home() }
}
