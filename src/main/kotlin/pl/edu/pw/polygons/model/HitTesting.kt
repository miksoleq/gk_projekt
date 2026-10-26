package pl.edu.pw.polygons.model

fun distanceToSegment(point: Point, start: Point, end: Point): Double {
    val direction = end - start
    val lengthSquared = direction.x * direction.x + direction.y * direction.y
    if (lengthSquared == 0.0) return point.distanceTo(start)
    val relative = point - start
    val t = ((relative.x * direction.x + relative.y * direction.y) / lengthSquared).coerceIn(0.0, 1.0)
    return point.distanceTo(start + direction * t)
}

/** Non-zero winding matches the renderer and includes the center of the self-intersecting star. */
fun containsPoint(polygon: Polygon, point: Point): Boolean {
    var winding = 0
    polygonBoundarySegments(polygon).forEach { (a, b) ->
        val side = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
        if (a.y <= point.y) {
            if (b.y > point.y && side > 0.0) winding++
        } else {
            if (b.y <= point.y && side < 0.0) winding--
        }
    }
    return winding != 0
}

private fun polygonBoundarySegments(polygon: Polygon): List<Pair<Point, Point>> {
    val segments = mutableListOf<Pair<Point, Point>>()
    polygon.edges.forEachIndexed { index, edge ->
        val start = polygon.edgeStart(index)
        val end = polygon.edgeEnd(index)
        if (edge.shape is EdgeShape.Line) {
            segments += start to end
        } else {
            val geometry = EdgeGeometry(edge.id, 0, 0, start, end, edge.shape)
            var previous = start
            for (step in 1..40) {
                val current = geometry.pointAt(step / 40.0)
                segments += previous to current
                previous = current
            }
        }
    }
    return segments
}
