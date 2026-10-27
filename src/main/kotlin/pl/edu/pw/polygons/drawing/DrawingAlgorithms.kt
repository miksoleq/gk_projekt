package pl.edu.pw.polygons.drawing

import pl.edu.pw.polygons.model.Point
import pl.edu.pw.polygons.model.Vector
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class LineAlgorithm(val label: String) {
    Library("Biblioteczny"),
    Bresenham("Bresenham"),
}
data class Pixel(val x: Int, val y: Int)
fun bresenhamLine(start: Pixel, end: Pixel, plot: (Pixel) -> Unit) {
    var x = start.x
    var y = start.y
    val dx = abs(end.x.toLong() - start.x)
    val dy = -abs(end.y.toLong() - start.y)
    val stepX = if (start.x < end.x) 1 else -1
    val stepY = if (start.y < end.y) 1 else -1
    var error = dx + dy

    while (true) {
        plot(Pixel(x, y))
        if (x == end.x && y == end.y) break
        val doubledError = 2 * error
        if (doubledError >= dy) {
            error += dy
            x += stepX
        }
        if (doubledError <= dx) {
            error += dx
            y += stepY
        }
    }
}
data class CubicPowerBasis(val a: Vector, val b: Vector, val c: Vector, val d: Point) {
    companion object {
        fun fromBezier(start: Point, first: Point, second: Point, end: Point): CubicPowerBasis {
            val firstOffset = first - start
            val secondOffset = second - start
            return CubicPowerBasis(
                a = (end - start) - secondOffset * 3.0 + firstOffset * 3.0,
                b = secondOffset * 3.0 - firstOffset * 6.0,
                c = firstOffset * 3.0,
                d = start,
            )
        }
    }
}
fun incrementalBezier(start: Point, first: Point, second: Point, end: Point, steps: Int): List<Point> {
    require(steps in 1..MAX_BEZIER_STEPS) { "Invalid number of Bezier steps" }
    val basis = CubicPowerBasis.fromBezier(start, first, second, end)
    val h = 1.0 / steps
    val h2 = h * h
    val h3 = h2 * h
    var point = basis.d
    var firstDifference = basis.a * h3 + basis.b * h2 + basis.c * h
    var secondDifference = basis.a * (6.0 * h3) + basis.b * (2.0 * h2)
    val thirdDifference = basis.a * (6.0 * h3)

    return buildList(steps + 1) {
        add(start)
        for (index in 1..steps) {
            point += firstDifference
            firstDifference += secondDifference
            secondDifference += thirdDifference
            add(if (index == steps) end else point)
        }
    }
}
fun bezierStepCount(start: Point, first: Point, second: Point, end: Point): Int =
    ceil(3.0 * maxOf(start.distanceTo(first), first.distanceTo(second), second.distanceTo(end)))
        .coerceIn(1.0, MAX_BEZIER_STEPS.toDouble()).toInt()

const val MAX_BEZIER_STEPS = 100_000
fun clipLine(start: Point, end: Point, left: Double, top: Double, right: Double, bottom: Double): Pair<Point, Point>? {
    require(left <= right && top <= bottom)
    if (!start.x.isFinite() || !start.y.isFinite() || !end.x.isFinite() || !end.y.isFinite()) return null
    val delta = end - start
    val p = doubleArrayOf(-delta.x, delta.x, -delta.y, delta.y)
    val q = doubleArrayOf(start.x - left, right - start.x, start.y - top, bottom - start.y)
    var firstT = 0.0
    var lastT = 1.0
    for (index in p.indices) {
        if (p[index] == 0.0) {
            if (q[index] < 0.0) return null
        } else {
            val t = q[index] / p[index]
            if (p[index] < 0.0) firstT = max(firstT, t) else lastT = min(lastT, t)
            if (firstT > lastT) return null
        }
    }
    fun clippedPoint(t: Double): Point {
        val point = start + delta * t
        return Point(point.x.coerceIn(left, right), point.y.coerceIn(top, bottom))
    }
    return clippedPoint(firstT) to clippedPoint(lastT)
}
