package com.unop2p.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unop2p.app.common.Prefs
import com.unop2p.app.net.IceConfig
import com.unop2p.app.qr.JoinTarget
import com.unop2p.app.session.ClientController
import com.unop2p.app.session.HostController
import com.unop2p.app.session.LocalGameController
import com.unop2p.engine.game.GameSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface Route {
    data object Home : Route
    data object Create : Route
    data object HostRoom : Route
    data object JoinEntry : Route
    data object Scan : Route
    data object ClientRoom : Route
    data object LocalSetup : Route
    data object LocalGame : Route
    data object Settings : Route
    data object Debug : Route
}

/** App-wide state: navigation stack, saved nickname, and the active session controllers. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var nickname by mutableStateOf("")
        private set

    // ICE / Internet-play settings (STUN on by default; TURN optional, user-supplied).
    var stunEnabled by mutableStateOf(true)
        private set
    var turnUrl by mutableStateOf("")
        private set
    var turnUser by mutableStateOf("")
        private set
    var turnCred by mutableStateOf("")
        private set

    private val stack = mutableStateListOf<Route>(Route.Home)
    val route: Route get() = stack.last()

    var host by mutableStateOf<HostController?>(null)
        private set
    var client by mutableStateOf<ClientController?>(null)
        private set
    var local by mutableStateOf<LocalGameController?>(null)
        private set

    var joinError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            nickname = prefs.nickname.first()
            stunEnabled = prefs.stunEnabled.first()
            turnUrl = prefs.turnUrl.first()
            turnUser = prefs.turnUser.first()
            turnCred = prefs.turnCred.first()
        }
    }

    private fun iceServers() = IceConfig.build(stunEnabled, turnUrl, turnUser, turnCred)

    fun updateStunEnabled(value: Boolean) {
        stunEnabled = value
        viewModelScope.launch { prefs.setStunEnabled(value) }
    }

    fun setTurn(url: String, user: String, cred: String) {
        turnUrl = url; turnUser = user; turnCred = cred
        viewModelScope.launch { prefs.setTurn(url, user, cred) }
    }

    // ---- Navigation ----------------------------------------------------------
    fun navigate(r: Route) { stack.add(r) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun home() { stack.clear(); stack.add(Route.Home) }

    fun updateNickname(value: String) {
        nickname = value.take(24)
        viewModelScope.launch { prefs.setNickname(nickname) }
    }

    // ---- Session lifecycle ---------------------------------------------------
    fun createGame(maxPlayers: Int, settings: GameSettings) {
        val h = HostController(
            getApplication(), nickname.ifBlank { "Host" }, maxPlayers, settings,
            iceServers = iceServers(),
        )
        host = h
        viewModelScope.launch(Dispatchers.IO) { h.start() }
        navigate(Route.HostRoom)
    }

    fun joinGame(target: JoinTarget) {
        joinError = null
        val c = ClientController(
            getApplication(), viewModelScope, nickname.ifBlank { "Player" },
            localIceServers = iceServers(),
        )
        client = c
        navigate(Route.ClientRoom)
        viewModelScope.launch {
            runCatching { c.connect(target.host, target.port, target.room) }
                .onFailure { joinError = it.message ?: "Could not connect" }
        }
    }

    fun startLocal(playerNames: List<String>) {
        local = LocalGameController(playerNames)
        navigate(Route.LocalGame)
    }

    fun leaveHost() {
        host?.shutdown(); host = null; home()
    }

    fun leaveClient() {
        val c = client
        client = null
        viewModelScope.launch { runCatching { c?.disconnect() } }
        home()
    }

    fun leaveLocal() { local = null; home() }

    override fun onCleared() {
        host?.shutdown()
        viewModelScope.launch { runCatching { client?.disconnect() } }
    }
}
