package pl.edu.pw.polygons.drawing

import pl.edu.pw.polygons.model.Point
import pl.edu.pw.polygons.model.Vector
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawingAlgorithmsTest {
    @Test
    fun `bresenham covers horizontal vertical diagonal and single pixel segments`() {
        assertEquals((2..7).map { Pixel(it, 3) }, pixels(Pixel(2, 3), Pixel(7, 3)))
        assertEquals((7 downTo 2).map { Pixel(3, it) }, pixels(Pixel(3, 7), Pixel(3, 2)))
        assertEquals((0..4).map { Pixel(it, it) }, pixels(Pixel(0, 0), Pixel(4, 4)))
        assertEquals(listOf(Pixel(8, -2)), pixels(Pixel(8, -2), Pixel(8, -2)))
        assertEquals(listOf(Pixel(0, 0), Pixel(1, 0), Pixel(2, 1), Pixel(3, 1), Pixel(4, 2), Pixel(5, 2)),
            pixels(Pixel(0, 0), Pixel(5, 2)))
    }

    @Test
    fun `bresenham has connected pixels and bounded error in every octant and direction`() {
        val origin = Pixel(12, -7)
        for (dx in -15..15) for (dy in -15..15) {
            val end = Pixel(origin.x + dx, origin.y + dy)
            for ((start, finish) in listOf(origin to end, end to origin)) {
                val result = pixels(start, finish)
                assertEquals(start, result.first())
                assertEquals(finish, result.last())
                assertEquals(max(abs(dx), abs(dy)) + 1, result.size)
                assertEquals(result.size, result.distinct().size)
                result.zipWithNext().forEach { (a, b) ->
                    assertTrue(abs(b.x - a.x) <= 1 && abs(b.y - a.y) <= 1)
                }
                if (dx != 0 || dy != 0) result.forEach { pixel ->
                    val distance = abs(dx.toDouble() * (pixel.y - origin.y) - dy.toDouble() * (pixel.x - origin.x)) / hypot(dx.toDouble(), dy.toDouble())
                    assertTrue(distance <= 0.500001, "Pixel $pixel is too far from segment $origin -> $end")
                }
            }
        }
    }

    @Test
    fun `power basis has expected cubic coefficients and retains a point as its origin`() {
        val basis = CubicPowerBasis.fromBezier(Point(10.0, 20.0), Point(10.0, 23.0), Point(13.0, 23.0), Point(13.0, 20.0))
        assertEquals(Vector(-6.0, 0.0), basis.a)
        assertEquals(Vector(9.0, -9.0), basis.b)
        assertEquals(Vector(0.0, 9.0), basis.c)
        assertEquals(Point(10.0, 20.0), basis.d)
    }

    @Test
    fun `forward differences match Bernstein evaluation including loops and straight curves`() {
        val curves = listOf(
            listOf(Point(20.0, 40.0), Point(300.0, -120.0), Point(-50.0, 400.0), Point(500.0, 210.0)),
            listOf(Point(10.0, 10.0), Point(160.0, -90.0), Point(-140.0, -90.0), Point(10.0, 10.0)),
            listOf(Point(0.0, 0.0), Point(20.0, 0.0), Point(40.0, 0.0), Point(60.0, 0.0)),
            List(4) { Point(5.0, 3.0) },
        )
        for (control in curves) for (steps in listOf(1, 2, 7, 256, 8192)) {
            val samples = incrementalBezier(control[0], control[1], control[2], control[3], steps)
            assertEquals(steps + 1, samples.size)
            assertEquals(control[0], samples.first())
            assertEquals(control[3], samples.last())
            samples.forEachIndexed { index, point ->
                val t = index.toDouble() / steps
                val u = 1.0 - t
                val weights = listOf(u*u*u, 3*u*u*t, 3*u*t*t, t*t*t)
                val expectedX = control.indices.sumOf { control[it].x * weights[it] }
                val expectedY = control.indices.sumOf { control[it].y * weights[it] }
                assertEquals(expectedX, point.x, 1e-6)
                assertEquals(expectedY, point.y, 1e-6)
            }
        }
    }

    @Test
    fun `sampling accounts for screen scale and for control handles of a closed curve`() {
        val a = Point(0.0, 0.0)
        assertEquals(1, bezierStepCount(a, a, a, a))
        val first = Point(20.0, 10.0)
        val second = Point(-20.0, 10.0)
        val count = bezierStepCount(a, first, second, a)
        val scaled = bezierStepCount(a, Point(40.0, 20.0), Point(-40.0, 20.0), a)
        assertTrue(count > 1)
        assertEquals(count * 2, scaled)
        assertFailsWith<IllegalArgumentException> { incrementalBezier(a, first, second, a, 0) }
    }

    @Test
    fun `clipping keeps crossing segments and discards invisible segments`() {
        assertEquals(Point(0.0, 5.0) to Point(10.0, 5.0),
            clipLine(Point(-1_000_000.0, 5.0), Point(1_000_000.0, 5.0), 0.0, 0.0, 10.0, 10.0))
        assertEquals(Point(5.0, 0.0) to Point(5.0, 10.0),
            clipLine(Point(5.0, -20.0), Point(5.0, 30.0), 0.0, 0.0, 10.0, 10.0))
        assertNull(clipLine(Point(-20.0, -1.0), Point(30.0, -1.0), 0.0, 0.0, 10.0, 10.0))
        assertNull(clipLine(Point(11.0, 5.0), Point(11.0, 5.0), 0.0, 0.0, 10.0, 10.0))
        assertEquals(Point(5.0, 5.0) to Point(5.0, 5.0),
            clipLine(Point(5.0, 5.0), Point(5.0, 5.0), 0.0, 0.0, 10.0, 10.0))
    }

    private fun pixels(start: Pixel, end: Pixel) = buildList { bresenhamLine(start, end) { add(it) } }
}
