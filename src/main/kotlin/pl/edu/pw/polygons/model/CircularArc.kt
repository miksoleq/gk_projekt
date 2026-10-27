package pl.edu.pw.polygons.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
data class CircularArcGeometry(val start: Point, val end: Point, val sweep: Double) {
    val center: Point
        get() {
            val chord = end - start
            return start + chord * 0.5 + chord.perpendicular() * (0.5 / tan(sweep * 0.5))
        }
    val radius get() = start.distanceTo(center)
    val length get() = radius * abs(sweep)

    fun pointAt(t: Double): Point {
        if (t == 0.0) return start
        if (t == 1.0) return end
        return center + (start - center).rotated(sweep * t)
    }

    val startTangent get() = (end - start).rotated(-sweep * 0.5).normalized()
    val endTangent get() = (end - start).rotated(sweep * 0.5).normalized()

    companion object {
        fun fromTangent(start: Point, end: Point, tangent: Vector, atStart: Boolean): CircularArcGeometry? {
            val chord = end - start
            if (chord.length() < 1e-9 || tangent.length() < 1e-9) return null
            val a = if (atStart) tangent.normalized() else chord.normalized()
            val b = if (atStart) chord.normalized() else tangent.normalized()
            val sweep = 2.0 * atan2(a.x * b.y - a.y * b.x, a.dot(b))
            if (abs(sweep) < 1e-7 || abs(sweep) > 2.0 * PI - 1e-7) return null
            return CircularArcGeometry(start, end, sweep)
        }
    }
}

private fun Vector.rotated(angle: Double) = Vector(
    x * cos(angle) - y * sin(angle),
    x * sin(angle) + y * cos(angle),
)
