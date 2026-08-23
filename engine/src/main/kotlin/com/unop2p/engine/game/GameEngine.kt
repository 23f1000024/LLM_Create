package com.unop2p.engine.game

import kotlin.random.Random

/** Outcome of applying an action: the (possibly unchanged) state, emitted events, and an optional rejection reason. */
data class EngineResult(
    val state: GameState,
    val events: List<GameEvent> = emptyList(),
    val error: String? = null,
) {
    val accepted: Boolean get() = error == null
}

/**
 * The authoritative UNO rules engine. Pure and deterministic given a [Random]:
 * it never touches the network, UI, or clocks. The host is the only component
 * that runs it; clients merely receive the resulting snapshots.
 *
 * All public entry points validate the action fully and reject illegal ones
 * (returning the original state unchanged with an [EngineResult.error]) — a
 * client can never drive the state into an illegal position.
 */
class GameEngine(private val random: Random = Random.Default) {

    // ---- Setup ---------------------------------------------------------------

    /**
     * Deals a new game. [playerSeeds] are (playerId, displayName) in seating order.
     * The initial discard is always a number card (non-number starters are left in
     * the draw pile), which keeps first-turn handling unambiguous.
     */
    fun startGame(
        gameId: String,
        playerSeeds: List<Pair<String, String>>,
        settings: GameSettings = GameSettings(),
    ): EngineResult {
        require(playerSeeds.size in 2..10) { "UNO needs 2..10 players" }
        val deck = ArrayDeque(Deck.shuffledStandardDeck(random))

        val players = playerSeeds.map { (id, name) ->
            val hand = ArrayList<Card>(settings.startingHandSize)
            repeat(settings.startingHandSize) { hand += deck.removeFirst() }
            Player(
                playerId = id,
                displayName = name,
                hand = hand,
                connectionState = ConnectionState.CONNECTED,
                isReady = true,
            )
        }

        // Pull the first NUMBER card as the starting discard; keep the rest in order.
        val remaining = ArrayList(deck)
        val starterIdx = remaining.indexOfFirst { it.kind == CardKind.NUMBER }
        check(starterIdx >= 0) { "No number card available to start (impossible with a standard deck)" }
        val starter = remaining.removeAt(starterIdx)

        val state = GameState(
            gameId = gameId,
            phase = GamePhase.PLAYING,
            players = players,
            currentPlayerIndex = 0,
            direction = Direction.CLOCKWISE,
            drawPile = remaining,
            discardPile = listOf(starter),
            chosenColor = null,
            pendingDraw = 0,
            pendingDrawKind = null,
            turnNumber = 1,
            stateVersion = 1,
            winnerId = null,
            settings = settings,
        )
        return EngineResult(
            state,
            listOf(
                GameEvent.GameStarted(players.first().playerId, starter),
                GameEvent.TurnChanged(players.first().playerId),
            ),
        )
    }

    // ---- Action dispatch -----------------------------------------------------

    fun apply(state: GameState, action: PlayerAction): EngineResult = when (action) {
        is PlayerAction.PlayCard -> playCard(state, action)
        is PlayerAction.DrawCard -> drawCardAction(state, action)
        is PlayerAction.ChooseColor -> chooseColor(state, action)
        is PlayerAction.CallUno -> callUno(state, action)
        is PlayerAction.ChallengeUno -> challengeUno(state, action)
        is PlayerAction.ChallengeDrawFour -> challengeDrawFour(state, action)
    }

    // ---- PlayCard ------------------------------------------------------------

    private fun playCard(state: GameState, action: PlayerAction.PlayCard): EngineResult {
        if (state.phase != GamePhase.PLAYING) return reject(state, "Not in a play phase")
        val current = state.currentPlayer ?: return reject(state, "No current player")
        if (current.playerId != action.playerId) return reject(state, "Not your turn")

        val card = current.card(action.cardUid)
            ?: return reject(state, "You do not hold card ${action.cardUid}")
        if (!GameRules.isPlayable(card, state)) return reject(state, "${card.label} is not playable now")
        if (card.kind.isWild && action.chosenColor != null && action.chosenColor == CardColor.WILD) {
            return reject(state, "Chosen color must be a concrete color")
        }

        val events = ArrayList<GameEvent>()
        val players = state.players.toMutableList()
        val curIdx = state.currentPlayerIndex

        // Was the Wild Draw Four a bluff? (Player held a card matching the active color.)
        val wasBluff = card.kind == CardKind.WILD_DRAW_FOUR &&
            GameRules.handHasColor(current.hand, state.activeColor)

        // Remove the card from the player's hand and place it on the discard pile.
        val newHand = current.hand.filterNot { it.uid == card.uid }
        var actor = current.copy(hand = newHand)
        events += GameEvent.CardPlayed(actor.playerId, card)

        // UNO bookkeeping for the player who just played.
        actor = applyUnoAfterPlay(actor, action.declareUno, events)
        players[curIdx] = actor

        var working = state.copy(
            players = players,
            discardPile = state.discardPile + card,
            stateVersion = state.stateVersion + 1,
        )

        // Win check first: an empty hand ends the game immediately.
        if (newHand.isEmpty()) {
            working = working.copy(phase = GamePhase.FINISHED, winnerId = actor.playerId)
            events += GameEvent.GameFinished(actor.playerId)
            return EngineResult(working, events)
        }

        // Apply the played card's effect on colors, pending draws and direction.
        when (card.kind) {
            CardKind.WILD, CardKind.WILD_DRAW_FOUR -> {
                if (card.kind == CardKind.WILD_DRAW_FOUR) {
                    working = working.copy(
                        pendingDraw = working.pendingDraw + card.kind.drawAmount,
                        pendingDrawKind = CardKind.WILD_DRAW_FOUR,
                        lastWildDrawFourPlayerId = actor.playerId,
                        lastWildDrawFourWasBluff = wasBluff,
                    )
                }
                return if (action.chosenColor != null) {
                    // Color supplied with the play: set it and advance immediately.
                    working = working.copy(chosenColor = action.chosenColor)
                    events += GameEvent.ColorChosen(actor.playerId, action.chosenColor)
                    EngineResult(advanceAfterPlay(working, card, events), events)
                } else {
                    // Defer: wait for a ChooseColor action from this player.
                    working = working.copy(
                        phase = GamePhase.AWAITING_COLOR,
                        awaitingColorPlayerId = actor.playerId,
                        chosenColor = null,
                    )
                    EngineResult(working, events)
                }
            }
            CardKind.DRAW_TWO -> {
                working = working.copy(
                    chosenColor = null,
                    pendingDraw = working.pendingDraw + card.kind.drawAmount,
                    pendingDrawKind = CardKind.DRAW_TWO,
                )
            }
            else -> {
                // Number / Skip / Reverse: colored top card clears any prior wild color.
                working = working.copy(chosenColor = null)
            }
        }

        return EngineResult(advanceAfterPlay(working, card, events), events)
    }

    /** Advances the turn accounting for Skip/Reverse/normal progression after a play. */
    private fun advanceAfterPlay(state: GameState, played: Card, events: MutableList<GameEvent>): GameState {
        val n = state.players.size
        var direction = state.direction
        var steps = 1

        when (played.kind) {
            CardKind.SKIP -> {
                steps = 2
                val skipped = GameRules.advanceIndex(state.currentPlayerIndex, 1, direction, n)
                events += GameEvent.PlayerSkipped(state.players[skipped].playerId)
            }
            CardKind.REVERSE -> {
                direction = direction.reversed()
                events += GameEvent.DirectionReversed(direction)
                if (n == 2) {
                    // Two-player Reverse behaves as a Skip: play returns to the same player.
                    steps = 2
                    val skipped = GameRules.advanceIndex(state.currentPlayerIndex, 1, direction, n)
                    events += GameEvent.PlayerSkipped(state.players[skipped].playerId)
                }
            }
            else -> steps = 1 // Number, Draw Two, Wilds: the target draws & forfeits on their turn.
        }

        val nextIdx = GameRules.advanceIndex(state.currentPlayerIndex, steps, direction, n)
        events += GameEvent.TurnChanged(state.players[nextIdx].playerId)
        return state.copy(
            direction = direction,
            currentPlayerIndex = nextIdx,
            turnNumber = state.turnNumber + 1,
        )
    }

    // ---- ChooseColor ---------------------------------------------------------

    private fun chooseColor(state: GameState, action: PlayerAction.ChooseColor): EngineResult {
        if (state.phase != GamePhase.AWAITING_COLOR) return reject(state, "Not awaiting a color")
        if (state.awaitingColorPlayerId != action.playerId) return reject(state, "Not yours to choose")
        if (action.color == CardColor.WILD) return reject(state, "Choose a concrete color")

        val events = ArrayList<GameEvent>()
        events += GameEvent.ColorChosen(action.playerId, action.color)
        var working = state.copy(
            phase = GamePhase.PLAYING,
            chosenColor = action.color,
            awaitingColorPlayerId = null,
            stateVersion = state.stateVersion + 1,
        )
        // Now perform the deferred turn advancement for the wild just played.
        val topWild = state.topCard!!
        working = advanceAfterPlay(working, topWild, events)
        return EngineResult(working, events)
    }

    // ---- DrawCard ------------------------------------------------------------

    private fun drawCardAction(state: GameState, action: PlayerAction.DrawCard): EngineResult {
        if (state.phase != GamePhase.PLAYING) return reject(state, "Not in a play phase")
        val current = state.currentPlayer ?: return reject(state, "No current player")
        if (current.playerId != action.playerId) return reject(state, "Not your turn")

        val events = ArrayList<GameEvent>()
        var working = state

        if (working.pendingDraw > 0) {
            // Forced to draw the pending penalty, then forfeit the turn (skipped).
            val count = working.pendingDraw
            working = drawInto(working, current.playerId, count, events)
            events += GameEvent.DrawPenaltyApplied(current.playerId, count)
            working = working.copy(pendingDraw = 0, pendingDrawKind = null)
            working = advancePlainTurn(working, events)
            return EngineResult(working.copy(stateVersion = working.stateVersion + 1), events)
        }

        // Normal draw of a single card, then the turn passes.
        working = drawInto(working, current.playerId, 1, events)
        events += GameEvent.CardsDrawn(current.playerId, 1)
        working = advancePlainTurn(working, events)
        return EngineResult(working.copy(stateVersion = working.stateVersion + 1), events)
    }

    /** Advances exactly one seat in the current direction (used after a draw). */
    private fun advancePlainTurn(state: GameState, events: MutableList<GameEvent>): GameState {
        val nextIdx = GameRules.advanceIndex(state.currentPlayerIndex, 1, state.direction, state.players.size)
        events += GameEvent.TurnChanged(state.players[nextIdx].playerId)
        return state.copy(currentPlayerIndex = nextIdx, turnNumber = state.turnNumber + 1)
    }

    // ---- UNO -----------------------------------------------------------------

    private fun callUno(state: GameState, action: PlayerAction.CallUno): EngineResult {
        val idx = state.indexOf(action.playerId)
        if (idx < 0) return reject(state, "Unknown player")
        val p = state.players[idx]
        // A player may declare when at one or two cards (i.e. about to reach UNO).
        if (p.handCount > 2) return reject(state, "Can only call UNO with two or fewer cards")
        val players = state.players.toMutableList()
        players[idx] = p.copy(hasDeclaredUno = true, unoVulnerable = false)
        return EngineResult(
            state.copy(players = players, stateVersion = state.stateVersion + 1),
            listOf(GameEvent.UnoDeclared(action.playerId)),
        )
    }

    private fun challengeUno(state: GameState, action: PlayerAction.ChallengeUno): EngineResult {
        val targetIdx = state.indexOf(action.targetPlayerId)
        if (targetIdx < 0) return reject(state, "Unknown target")
        val target = state.players[targetIdx]
        if (!(target.unoVulnerable && target.handCount == 1)) {
            return reject(state, "Target is not catchable")
        }
        val events = ArrayList<GameEvent>()
        var working = drawInto(state, target.playerId, state.settings.unoPenaltyCards, events)
        // Clear vulnerability after the penalty.
        val players = working.players.toMutableList()
        val tIdx = working.indexOf(action.targetPlayerId)
        players[tIdx] = players[tIdx].copy(unoVulnerable = false, hasDeclaredUno = false)
        working = working.copy(players = players, stateVersion = working.stateVersion + 1)
        events += GameEvent.UnoPenalty(action.targetPlayerId, state.settings.unoPenaltyCards)
        return EngineResult(working, events)
    }

    /** Applies UNO state to the player who just played a card. */
    private fun applyUnoAfterPlay(actor: Player, declared: Boolean, events: MutableList<GameEvent>): Player {
        return when {
            actor.hand.size == 1 && declared -> {
                events += GameEvent.UnoDeclared(actor.playerId)
                actor.copy(hasDeclaredUno = true, unoVulnerable = false)
            }
            actor.hand.size == 1 -> actor.copy(hasDeclaredUno = false, unoVulnerable = true)
            else -> actor.copy(hasDeclaredUno = false, unoVulnerable = false)
        }
    }

    // ---- Wild Draw Four challenge -------------------------------------------

    private fun challengeDrawFour(state: GameState, action: PlayerAction.ChallengeDrawFour): EngineResult {
        if (!state.settings.enableDrawFourChallenge) return reject(state, "Challenges are disabled")
        if (state.pendingDrawKind != CardKind.WILD_DRAW_FOUR || state.pendingDraw <= 0) {
            return reject(state, "No Wild Draw Four to challenge")
        }
        val current = state.currentPlayer ?: return reject(state, "No current player")
        if (current.playerId != action.playerId) return reject(state, "Only the player facing the card may challenge")
        val targetId = state.lastWildDrawFourPlayerId ?: return reject(state, "No challenge target")

        val events = ArrayList<GameEvent>()
        val pending = state.pendingDraw
        return if (state.lastWildDrawFourWasBluff) {
            // Challenge succeeds: the bluffer draws the penalty; challenger keeps their turn.
            var working = drawInto(state, targetId, pending, events)
            working = working.copy(
                pendingDraw = 0,
                pendingDrawKind = null,
                lastWildDrawFourPlayerId = null,
                stateVersion = working.stateVersion + 1,
            )
            events += GameEvent.DrawFourChallenge(action.playerId, targetId, successful = true, penaltyCards = pending)
            EngineResult(working, events)
        } else {
            // Challenge fails: challenger draws the penalty plus two, then forfeits the turn.
            val penalty = pending + 2
            var working = drawInto(state, current.playerId, penalty, events)
            working = working.copy(
                pendingDraw = 0,
                pendingDrawKind = null,
                lastWildDrawFourPlayerId = null,
            )
            events += GameEvent.DrawFourChallenge(action.playerId, targetId, successful = false, penaltyCards = penalty)
            working = advancePlainTurn(working, events)
            EngineResult(working.copy(stateVersion = working.stateVersion + 1), events)
        }
    }

    // ---- Drawing / reshuffle -------------------------------------------------

    /**
     * Draws [count] cards into player [playerId]'s hand, reshuffling the discard
     * pile (all but the top card) into the draw pile when it runs dry. If both
     * piles are exhausted, fewer than [count] cards may be drawn.
     */
    private fun drawInto(state: GameState, playerId: String, count: Int, events: MutableList<GameEvent>): GameState {
        var drawPile = ArrayDeque(state.drawPile)
        var discardPile = state.discardPile.toMutableList()
        val drawn = ArrayList<Card>(count)

        repeat(count) {
            if (drawPile.isEmpty()) {
                // Reshuffle: keep the top discard, shuffle the rest into a new draw pile.
                if (discardPile.size <= 1) return@repeat // nothing left to draw
                val top = discardPile.removeAt(discardPile.lastIndex)
                val reshuffled = Deck.shuffled(discardPile, random)
                drawPile = ArrayDeque(reshuffled)
                discardPile = mutableListOf(top)
                events += GameEvent.DeckReshuffled(drawPile.size)
            }
            if (drawPile.isNotEmpty()) drawn += drawPile.removeFirst()
        }

        val idx = state.indexOf(playerId)
        val players = state.players.toMutableList()
        val p = players[idx]
        // Drawing beyond one card means the player is no longer at UNO.
        val newSize = p.hand.size + drawn.size
        players[idx] = p.copy(
            hand = p.hand + drawn,
            hasDeclaredUno = if (newSize > 1) false else p.hasDeclaredUno,
            unoVulnerable = if (newSize > 1) false else p.unoVulnerable,
        )
        return state.copy(players = players, drawPile = drawPile.toList(), discardPile = discardPile)
    }

    private fun reject(state: GameState, reason: String): EngineResult = EngineResult(state, emptyList(), reason)
}
