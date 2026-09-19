package dev.cernoh.quotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The size class and the line limit decide how much of the card survives a small
 * placement. Get either wrong and the attribution disappears, or the text is
 * oversized for the cell the launcher gave.
 */
class WidgetSizingTest {
    @Test
    fun heightPicksTheClass() {
        assertEquals(WidgetSize.TINY, WidgetSizing.sizeFor(300, 0))
        assertEquals(WidgetSize.TINY, WidgetSizing.sizeFor(300, 149))
        assertEquals(WidgetSize.COMPACT, WidgetSizing.sizeFor(300, 150))
        assertEquals(WidgetSize.COMPACT, WidgetSizing.sizeFor(300, 199))
        assertEquals(WidgetSize.REGULAR, WidgetSizing.sizeFor(300, 200))
        assertEquals(WidgetSize.REGULAR, WidgetSizing.sizeFor(300, 259))
        assertEquals(WidgetSize.TALL, WidgetSizing.sizeFor(300, 260))
        assertEquals(WidgetSize.TALL, WidgetSizing.sizeFor(300, 600))
    }

    @Test
    fun aNarrowCardDropsOneClass() {
        assertEquals(WidgetSize.TINY, WidgetSizing.sizeFor(150, 150))
        assertEquals(WidgetSize.COMPACT, WidgetSizing.sizeFor(150, 200))
        assertEquals(WidgetSize.REGULAR, WidgetSizing.sizeFor(150, 260))
        assertEquals(WidgetSize.REGULAR, WidgetSizing.sizeFor(199, 600))
        // The class stops dropping at TINY.
        assertEquals(WidgetSize.TINY, WidgetSizing.sizeFor(120, 100))
        // A width of 200dp is not narrow.
        assertEquals(WidgetSize.TALL, WidgetSizing.sizeFor(200, 260))
    }

    @Test
    fun theCurrentSizeUsesTheRightPairOfOptions() {
        // Measured on the emulator for a 3 by 2 placement.
        assertEquals(
            224 to 284,
            WidgetSizing.currentSize(
                portrait = true,
                minWidth = 224,
                minHeight = 136,
                maxWidth = 434,
                maxHeight = 284,
            ),
        )
        assertEquals(
            434 to 136,
            WidgetSizing.currentSize(
                portrait = false,
                minWidth = 224,
                minHeight = 136,
                maxWidth = 434,
                maxHeight = 284,
            ),
        )
    }

    @Test
    fun aShortCellGetsTheSmallCard() {
        // A short cell, for example a dense grid or a landscape home screen.
        val tier = WidgetSizing.tierFor(224, 136)
        assertEquals(15f, tier.quoteSizeSp, 0.01f)
        assertEquals(12, tier.paddingDp)
        assertTrue("the author must stay", tier.maxLines >= 3)
        assertTrue("the rule is not worth its space here", !tier.showRule)
    }

    @Test
    fun aThreeByTwoCellOnATallGridFillsTheCard() {
        // Measured on the emulator: a 3 by 2 placement reports 224x284dp.
        val tier = WidgetSizing.tierFor(224, 284)
        assertEquals(18f, tier.quoteSizeSp, 0.01f)
        assertTrue("a tall cell takes more lines", tier.maxLines >= 6)
        assertTrue(tier.showWork)
    }

    @Test
    fun aFourByThreePlacementGetsTheFullCard() {
        val tier = WidgetSizing.tierFor(320, 250)
        assertEquals(17f, tier.quoteSizeSp, 0.01f)
        assertEquals(6, tier.maxLines)
        assertTrue(tier.showRule)
        assertTrue(tier.showWork)
    }

    @Test
    fun aTallPlacementGetsMoreLinesThanAShortOne() {
        val short = WidgetSizing.tierFor(320, 140)
        val medium = WidgetSizing.tierFor(320, 240)
        val tall = WidgetSizing.tierFor(320, 400)
        assertTrue(short.maxLines < medium.maxLines)
        assertTrue(medium.maxLines < tall.maxLines)
        assertTrue(short.quoteSizeSp < tall.quoteSizeSp)
    }

    @Test
    fun everyTierKeepsAtLeastOneLine() {
        // A launcher that reports nothing, or a very short cell, must not
        // produce a card with no quote at all.
        listOf(0 to 0, 1 to 1, 300 to 60, 100 to 100).forEach { (width, height) ->
            val tier = WidgetSizing.tierFor(width, height)
            assertTrue("$width x $height needs a line", tier.maxLines >= 1)
            assertTrue("$width x $height needs a size", tier.quoteSizeSp > 0f)
        }
    }

    @Test
    fun everyClassIsUsableOnItsOwn() {
        WidgetSize.entries.forEach { size ->
            val base = WidgetSizing.tierFor(size)
            assertTrue("$size needs a line", base.maxLines >= 1)
            assertTrue("$size needs a readable size", base.quoteSizeSp >= 14f)
            assertTrue("$size needs a readable attribution", base.attributionSizeSp >= 10f)
            assertTrue("$size needs padding", base.paddingDp > 0)
        }
    }

    @Test
    fun fittingNeverGrowsTheLineLimit() {
        WidgetSize.entries.forEach { size ->
            val base = WidgetSizing.tierFor(size)
            listOf(120, 200, 400, 900).forEach { height ->
                val fitted = WidgetSizing.tierFor(320, height)
                assertTrue(fitted.maxLines <= WidgetSizing.tierFor(WidgetSize.TALL).maxLines)
            }
            assertTrue(base.maxLines <= WidgetSizing.tierFor(WidgetSize.TALL).maxLines)
        }
    }
}
