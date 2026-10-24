package app.orbit.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The measurements and colours every Orbit screen is drawn from.
 *
 * They were copied into four files while each screen was written. One copy means a tile on the
 * home screen and a tile in the picker cannot drift apart, which they had already started to do.
 */

// Sizes come straight from section B of the requirements.
internal val SafeH = 48.dp
internal val SafeV = 27.dp
internal val TileW = 111.dp
internal val TileH = 66.dp
// Narrow enough that a typical set's inputs fit the safe area without the row scrolling. A
// scrolling row slides its first tile out of line with the apps above it.
internal val InputW = 100.dp
/** How much a focused tile grows, about what the stock launcher's own tiles do. */
internal const val FocusScale = 1.2f

/** How far a focused tile reaches past each of its edges. */
internal val PopOut = TileH * (FocusScale - 1f) / 2

/** Room above the tiles. A row clips at its sides only, so a grown tile can reach past it. */
internal val RowTop = 4.dp

/** Between a tile and its label, far enough that the grown tile does not cover the label. */
internal val LabelGap = PopOut + 4.dp

/**
 * Both rows: the tiles, the gap and a label line. Fixed, so the rows below hold still while a
 * list is still loading.
 */
internal val RowHeight = RowTop + TileH + LabelGap + 16.dp

/**
 * A library poster, at 2:3, which is the shape the publishing apps draw their covers in.
 *
 * Measured against the screen rather than chosen: at 960x540dp the hero floor, the two rows of
 * tiles and both safe margins leave about 230dp once the inputs row moves below the fold, and
 * this is what fits in it with its label and its growing room.
 */
internal val PosterW = 85.dp
internal val PosterH = 128.dp

/** Wide enough for the tile on this panel, and a fraction of what a backdrop costs to fetch. */
internal const val PosterFetchPx = 256

/**
 * The poster row: a poster, the room above it and the room a focused one grows into. No label
 * line, because a cover carries its own title.
 */
internal val PosterRowHeight = RowTop + PosterH * FocusScale + 4.dp

/**
 * Between the hero and the first row. Fixed rather than whatever is left over: with the poster
 * row in, the screen is full and there is nothing left to share out.
 */
internal val HeroGap = 30.dp

internal val Surface = Color(0xFF14181F)
internal val OnSurface = Color(0xFFDDE5F0)
internal val Muted = Color(0xFF8B93A4)
internal val LastInput = Color(0xFF1AA3C4)
internal val Editing = Color(0xFFFFC14D)
internal val Glyph = Color(0xFFBFC9DA)
internal val Scrim = Color(0xB3000000)
internal val MenuCard = Color(0xFFDADCE0)
internal val MenuLit = Color.White
internal val MenuInk = Color(0xFF202124)

/** Room above a tile in a picker grid, so the focused one can grow without being clipped. */
internal val PopRoom = 6.dp

/** Already a favorite, or already on the home screen. */
internal val Chosen = Color(0xFF2E9E4F)
