package app.orbit.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * The pictures Orbit ships with.
 *
 * Drawn in code rather than bundled as files: nothing to licence, nothing to attribute, and a few
 * hundred bytes instead of a drawable set. Each is a gradient disc with one simple motif, which
 * stays readable at the size the top bar shows it.
 */
object BuiltInAvatars {

    enum class Motif { ORBIT, RINGS, SPLIT, DOT, CHEVRON, WAVE, TRIANGLE, PLAIN }

    data class BuiltInAvatar(
        val id: String,
        val name: String,
        val from: Int,
        val to: Int,
        val motif: Motif,
        val ink: Int = 0xFFFFFFFF.toInt(),
    )

    val all: List<BuiltInAvatar> = listOf(
        BuiltInAvatar("orbit", "Orbit", 0xFF1AA3C4.toInt(), 0xFF0D4E73.toInt(), Motif.ORBIT),
        BuiltInAvatar("dusk", "Dusk", 0xFF7C4DFF.toInt(), 0xFF2A1A6B.toInt(), Motif.RINGS),
        BuiltInAvatar("ember", "Ember", 0xFFFF7043.toInt(), 0xFF8E2A12.toInt(), Motif.SPLIT),
        BuiltInAvatar("moss", "Moss", 0xFF4CAF50.toInt(), 0xFF1B5E20.toInt(), Motif.DOT),
        BuiltInAvatar("slate", "Slate", 0xFF607D8B.toInt(), 0xFF263238.toInt(), Motif.CHEVRON),
        BuiltInAvatar("tide", "Tide", 0xFF26C6DA.toInt(), 0xFF00566B.toInt(), Motif.WAVE),
        BuiltInAvatar("bloom", "Bloom", 0xFFEC407A.toInt(), 0xFF7B1B3F.toInt(), Motif.TRIANGLE),
        BuiltInAvatar("ink", "Ink", 0xFF37474F.toInt(), 0xFF11171A.toInt(), Motif.PLAIN),
    )

    fun render(avatar: BuiltInAvatar, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f

        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                avatar.from, avatar.to, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(radius, radius, radius, disc)

        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = avatar.ink
            style = Paint.Style.STROKE
            strokeWidth = size * 0.07f
        }
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = avatar.ink }

        when (avatar.motif) {
            Motif.ORBIT -> {
                canvas.drawCircle(radius, radius, size * 0.26f, ink)
                canvas.drawCircle(radius + size * 0.26f, radius, size * 0.08f, solid)
            }
            Motif.RINGS -> {
                canvas.drawCircle(radius, radius, size * 0.3f, ink)
                canvas.drawCircle(radius, radius, size * 0.16f, ink)
            }
            Motif.SPLIT -> canvas.drawRect(0f, radius - size * 0.035f, size.toFloat(), radius + size * 0.035f, solid)
            Motif.DOT -> canvas.drawCircle(radius, radius, size * 0.2f, solid)
            Motif.CHEVRON -> {
                val path = Path().apply {
                    moveTo(size * 0.36f, size * 0.3f)
                    lineTo(size * 0.64f, radius)
                    lineTo(size * 0.36f, size * 0.7f)
                }
                canvas.drawPath(path, ink.apply { style = Paint.Style.STROKE })
            }
            Motif.WAVE -> {
                val path = Path().apply {
                    moveTo(size * 0.22f, radius)
                    quadTo(size * 0.36f, radius - size * 0.18f, radius, radius)
                    quadTo(size * 0.64f, radius + size * 0.18f, size * 0.78f, radius)
                }
                canvas.drawPath(path, ink)
            }
            Motif.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(radius, size * 0.3f)
                    lineTo(size * 0.7f, size * 0.66f)
                    lineTo(size * 0.3f, size * 0.66f)
                    close()
                }
                canvas.drawPath(path, solid)
            }
            Motif.PLAIN -> Unit
        }
        return bitmap
    }
}
