package pl.edu.pw.polygons.model

internal fun Edge.translateBy(delta: Vector): Edge = when (val current = shape) {
    EdgeShape.Line, is EdgeShape.CircularArc -> this
    is EdgeShape.CubicBezier -> copy(shape = current.copy(
        firstControl = current.firstControl + delta,
        secondControl = current.secondControl + delta,
    ))
}

internal fun splitBezier(edge: EdgeGeometry): Pair<EdgeShape.CubicBezier, EdgeShape.CubicBezier> {
    val a = edge.start + (edge.firstControl - edge.start) * 0.5
    val b = edge.firstControl + (edge.secondControl - edge.firstControl) * 0.5
    val c = edge.secondControl + (edge.end - edge.secondControl) * 0.5
    val d = a + (b - a) * 0.5
    val e = b + (c - b) * 0.5
    return EdgeShape.CubicBezier(a, d) to EdgeShape.CubicBezier(e, c)
}
