package pl.edu.pw.polygons.model

data class EdgeGeometry(
    val edgeId: Long,
    val startVertexId: Long,
    val endVertexId: Long,
    val start: Point,
    val end: Point,
    val shape: EdgeShape,
) {
    val direction get() = end - start
    val chordLength get() = direction.length()
    val isBezier get() = shape is EdgeShape.CubicBezier
    val isArc get() = shape is EdgeShape.CircularArc
    val arc: CircularArcGeometry?
        get() = (shape as? EdgeShape.CircularArc)?.let { CircularArcGeometry(start, end, it.sweep) }
    val firstControl get() = when (val current = shape) {
        EdgeShape.Line, is EdgeShape.CircularArc -> start + direction * ONE_THIRD
        is EdgeShape.CubicBezier -> current.firstControl
    }
    val secondControl get() = when (val current = shape) {
        EdgeShape.Line, is EdgeShape.CircularArc -> start + direction * TWO_THIRDS
        is EdgeShape.CubicBezier -> current.secondControl
    }
    val startTangent get() = arc?.startTangent ?: (firstControl - start)
    val endTangent get() = arc?.endTangent ?: (end - secondControl)
    val length get() = arc?.length ?: if (isBezier) arcLength() else chordLength

    fun pointAt(t: Double): Point {
        arc?.let { return it.pointAt(t) }
        val u = 1.0 - t
        return start + (firstControl - start) * (3.0 * u * u * t) +
            (secondControl - start) * (3.0 * u * t * t) +
            (end - start) * (t * t * t)
    }

    fun arcLength(steps: Int = 40): Double {
        var result = 0.0
        var previous = start
        for (index in 1..steps) {
            val current = pointAt(index.toDouble() / steps)
            result += previous.distanceTo(current)
            previous = current
        }
        return result
    }
}

private const val ONE_THIRD = 1.0 / 3.0
private const val TWO_THIRDS = 2.0 / 3.0
