package com.unop2p.app.session

import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.GameEngine
import com.unop2p.engine.game.GameSettings
import com.unop2p.engine.game.GameState
import com.unop2p.engine.game.PlayerAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Offline "hot-seat" mode: one device, several local players passing the phone.
 * It drives the exact same [GameEngine] as the networked host, with zero
 * networking — which is what makes the engine testable/playable in isolation.
 */
class LocalGameController(
    playerNames: List<String>,
    settings: GameSettings = GameSettings(),
    random: Random = Random.Default,
) {
    private val engine = GameEngine(random)
    private val _state = MutableStateFlow(
        engine.startGame(
            gameId = "local",
            playerSeeds = playerNames.mapIndexed { i, n -> "local_$i" to n },
            settings = settings,
        ).state,
    )
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** The player whose turn it currently is — the one who should be holding the phone. */
    val currentPlayerId: String? get() = _state.value.currentPlayer?.playerId

    fun play(cardUid: String, chosenColor: CardColor? = null, declareUno: Boolean = false) =
        apply(PlayerAction.PlayCard(requireCurrent(), cardUid, chosenColor, declareUno))
    fun draw() = apply(PlayerAction.DrawCard(requireCurrent()))
    fun chooseColor(color: CardColor) = apply(PlayerAction.ChooseColor(requireCurrent(), color))
    fun callUno() = apply(PlayerAction.CallUno(requireCurrent()))

    private fun requireCurrent(): String = _state.value.currentPlayer?.playerId ?: _state.value.players.first().playerId

    private fun apply(action: PlayerAction) {
        val result = engine.apply(_state.value, action)
        if (result.accepted) {
            _state.value = result.state
            _lastError.value = null
        } else {
            _lastError.value = result.error
        }
    }

    fun clearError() { _lastError.value = null }
}
