package dev.cernoh.quotes

/** How large the placed widget is, in the terms the card needs. */
enum class WidgetSize {
    TINY,
    COMPACT,
    REGULAR,
    TALL,
}

/** The card measurements for one size. */
data class WidgetTier(
    val quoteSizeSp: Float,
    val attributionSizeSp: Float,
    val maxLines: Int,
    val paddingDp: Int,
    val showRule: Boolean,
    val showWork: Boolean,
)

/**
 * Chooses the card measurements from the size the launcher gave the widget.
 *
 * A home screen cell is not a fixed size, so the card cannot be either. The
 * launcher reports what it gave the widget, and this object turns that into a
 * text size, a line limit and a padding.
 *
 * The line limit is computed from the height rather than fixed per class: a
 * class whose four lines do not fit the given height drops to a smaller class,
 * so the attribution always stays on the card. Measured on an emulator, a 3 by 2
 * placement is 224x136dp.
 *
 * This object holds no Android types, so a plain unit test covers every case.
 */
object WidgetSizing {
    /** At least this many quote lines, or the next smaller class is used. */
    const val MIN_LINES = 3

    // Height in dp at which the next class starts.
    private const val HEIGHT_COMPACT = 150
    private const val HEIGHT_REGULAR = 200
    private const val HEIGHT_TALL = 260

    // Below this width the text needs one class less, or the lines wrap twice
    // as often and the card overflows anyway.
    private const val NARROW_WIDTH = 200

    // Space one line of text takes, as a multiple of its size.
    private const val LINE_HEIGHT = 1.35

    // Space the rule and its margins take, and the same when it is not drawn.
    private const val RULE_BLOCK_DP = 23
    private const val RULE_BLOCK_HIDDEN_DP = 6

    private val tiers = mapOf(
        WidgetSize.TINY to WidgetTier(
            quoteSizeSp = 15f,
            attributionSizeSp = 10f,
            maxLines = 5,
            paddingDp = 12,
            showRule = false,
            showWork = false,
        ),
        WidgetSize.COMPACT to WidgetTier(
            quoteSizeSp = 16f,
            attributionSizeSp = 11f,
            maxLines = 4,
            paddingDp = 14,
            showRule = true,
            showWork = true,
        ),
        WidgetSize.REGULAR to WidgetTier(
            quoteSizeSp = 17f,
            attributionSizeSp = 12f,
            maxLines = 6,
            paddingDp = 18,
            showRule = true,
            showWork = true,
        ),
        WidgetSize.TALL to WidgetTier(
            quoteSizeSp = 18f,
            attributionSizeSp = 12f,
            maxLines = 9,
            paddingDp = 20,
            showRule = true,
            showWork = true,
        ),
    )

    private val order = listOf(WidgetSize.TALL, WidgetSize.REGULAR, WidgetSize.COMPACT, WidgetSize.TINY)

    /**
     * The size the launcher currently gives the widget, in dp.
     *
     * The bundle carries four numbers. In portrait the current size is the
     * minimum width and the maximum height; in landscape it is the maximum width
     * and the minimum height. Reading the wrong pair sizes the card for a cell
     * the widget does not have. Measured on an emulator, a 3 by 2 placement
     * reports min 224x136 and max 434x284, and the widget draws 224 deep by 284
     * high.
     */
    fun currentSize(
        portrait: Boolean,
        minWidth: Int,
        minHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Pair<Int, Int> = if (portrait) minWidth to maxHeight else maxWidth to minHeight

    /** The size class for a placed width and height in dp. */
    fun sizeFor(widthDp: Int, heightDp: Int): WidgetSize {
        val byHeight = when {
            heightDp >= HEIGHT_TALL -> WidgetSize.TALL
            heightDp >= HEIGHT_REGULAR -> WidgetSize.REGULAR
            heightDp >= HEIGHT_COMPACT -> WidgetSize.COMPACT
            else -> WidgetSize.TINY
        }
        if (widthDp in 1 until NARROW_WIDTH) {
            return WidgetSize.entries[(byHeight.ordinal - 1).coerceAtLeast(0)]
        }
        return byHeight
    }

    /** The card measurements that fit a placed width and height in dp. */
    fun tierFor(widthDp: Int, heightDp: Int): WidgetTier {
        val start = order.indexOf(sizeFor(widthDp, heightDp)).coerceAtLeast(0)
        for (index in start until order.size) {
            val tier = fitted(tiers.getValue(order[index]), heightDp)
            if (tier.maxLines >= MIN_LINES) return tier
        }
        return fitted(tiers.getValue(WidgetSize.TINY), heightDp)
    }

    /** The measurements of one class, without fitting them to a height. */
    fun tierFor(size: WidgetSize): WidgetTier = tiers.getValue(size)

    /**
     * Cut the line limit down until the quote, the attribution and the padding
     * fit the given height.
     */
    private fun fitted(tier: WidgetTier, heightDp: Int): WidgetTier {
        val attribution = tier.attributionSizeSp * LINE_HEIGHT *
            (if (tier.showWork) 2 else 1)
        val rule = if (tier.showRule) RULE_BLOCK_DP else RULE_BLOCK_HIDDEN_DP
        val chrome = 2 * tier.paddingDp + rule + attribution
        val lineHeight = tier.quoteSizeSp * LINE_HEIGHT
        val room = (heightDp - chrome) / lineHeight
        val lines = room.toInt().coerceIn(1, tier.maxLines)
        return tier.copy(maxLines = lines)
    }
}
