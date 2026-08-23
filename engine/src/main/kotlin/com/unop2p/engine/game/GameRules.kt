package com.unop2p.engine.game

/**
 * Pure, side-effect-free rule helpers. Used by the [GameEngine] to validate
 * actions and by clients to render legal moves. Contains no mutable state.
 */
object GameRules {

    /**
     * Whether [card] may legally be played given the current [state]. This is the
     * single source of truth for card legality and is enforced by the host.
     */
    fun isPlayable(card: Card, state: GameState): Boolean = isPlayable(
        card = card,
        topCard = state.topCard,
        activeColor = state.activeColor,
        pendingDraw = state.pendingDraw,
        pendingDrawKind = state.pendingDrawKind,
        allowStacking = state.settings.allowStacking,
    )

    /**
     * Param-based legality core, so both the authoritative [GameState] and the
     * clients' redacted view (which carries the same public fields) can compute
     * legal moves identically.
     */
    fun isPlayable(
        card: Card,
        topCard: Card?,
        activeColor: CardColor?,
        pendingDraw: Int,
        pendingDrawKind: CardKind?,
        allowStacking: Boolean,
    ): Boolean {
        val top = topCard ?: return true

        // A draw penalty is pending: normally you must draw. Only when stacking is
        // enabled may you play a compatible draw card to pass the penalty on.
        if (pendingDraw > 0) {
            if (!allowStacking) return false
            return when (pendingDrawKind) {
                CardKind.DRAW_TWO -> card.kind == CardKind.DRAW_TWO || card.kind == CardKind.WILD_DRAW_FOUR
                CardKind.WILD_DRAW_FOUR -> card.kind == CardKind.WILD_DRAW_FOUR
                else -> false
            }
        }

        // Wild cards are always playable. (Wild Draw Four legality is resolved by
        // the optional bluff challenge, not by blocking the play.)
        if (card.kind.isWild) return true

        if (card.color == activeColor) return true
        if (card.kind == CardKind.NUMBER && top.kind == CardKind.NUMBER && card.number == top.number) return true
        // Symbol match: e.g. Skip on Skip regardless of color.
        if (card.kind != CardKind.NUMBER && card.kind == top.kind) return true
        return false
    }

    /** True if [hand] contains any non-wild card whose color equals [color]. */
    fun handHasColor(hand: List<Card>, color: CardColor?): Boolean {
        if (color == null || color == CardColor.WILD) return false
        return hand.any { !it.kind.isWild && it.color == color }
    }

    /** True if the player has at least one legal move given the state. */
    fun hasAnyPlayableCard(hand: List<Card>, state: GameState): Boolean =
        hand.any { isPlayable(it, state) }

    /** Index of the player [steps] seats away from [from] in the current [direction]. */
    fun advanceIndex(from: Int, steps: Int, direction: Direction, playerCount: Int): Int {
        require(playerCount > 0)
        val raw = (from + direction.step * steps) % playerCount
        return (raw + playerCount) % playerCount
    }
}
