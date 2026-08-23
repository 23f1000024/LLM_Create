package com.unop2p.engine.game

/** Deterministic card + state builders for engine tests. */
object TestCards {
    private var seq = 0
    private fun uid(tag: String) = "t${seq++}_$tag"

    fun num(color: CardColor, n: Int) = Card(uid("${color}_$n"), color, CardKind.NUMBER, n)
    fun skip(color: CardColor) = Card(uid("${color}_SKIP"), color, CardKind.SKIP)
    fun reverse(color: CardColor) = Card(uid("${color}_REV"), color, CardKind.REVERSE)
    fun drawTwo(color: CardColor) = Card(uid("${color}_D2"), color, CardKind.DRAW_TWO)
    fun wild() = Card(uid("WILD"), CardColor.WILD, CardKind.WILD)
    fun wildDrawFour() = Card(uid("WD4"), CardColor.WILD, CardKind.WILD_DRAW_FOUR)

    /** Builds a PLAYING state with controlled hands, top card and draw pile. */
    fun playing(
        hands: List<Pair<String, List<Card>>>,
        top: Card,
        currentIndex: Int = 0,
        direction: Direction = Direction.CLOCKWISE,
        drawPile: List<Card> = List(20) { num(CardColor.RED, it % 10) },
        chosenColor: CardColor? = null,
        pendingDraw: Int = 0,
        pendingDrawKind: CardKind? = null,
        settings: GameSettings = GameSettings(),
        lastWildDrawFourPlayerId: String? = null,
        lastWildDrawFourWasBluff: Boolean = false,
    ): GameState = GameState(
        gameId = "test",
        phase = GamePhase.PLAYING,
        players = hands.map { (id, hand) ->
            Player(id, id, hand, ConnectionState.CONNECTED, isReady = true)
        },
        currentPlayerIndex = currentIndex,
        direction = direction,
        drawPile = drawPile,
        discardPile = listOf(top),
        chosenColor = chosenColor,
        pendingDraw = pendingDraw,
        pendingDrawKind = pendingDrawKind,
        turnNumber = 1,
        stateVersion = 1,
        winnerId = null,
        settings = settings,
        lastWildDrawFourPlayerId = lastWildDrawFourPlayerId,
        lastWildDrawFourWasBluff = lastWildDrawFourWasBluff,
    )
}
