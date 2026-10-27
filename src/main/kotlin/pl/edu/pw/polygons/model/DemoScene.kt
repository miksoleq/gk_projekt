package pl.edu.pw.polygons.model

fun demoScene(withConstraints: Boolean = true): Scene {
    var next = 1L
    fun polygon(name: String, points: List<Point>): Polygon {
        val id = next++
        val vertices = points.map { Vertex(next++, it) }
        val edges = points.map { Edge(next++) }
        return Polygon(id, name, vertices, edges)
    }
    val base = Scene(
        polygons = listOf(
            polygon("Gwiazda", listOf(
                Point(135.0, 105.0), Point(260.0, 325.0), Point(55.0, 185.0),
                Point(290.0, 185.0), Point(85.0, 325.0),
            )),
            polygon("Pięciokąt", listOf(
                Point(375.0, 105.0), Point(535.0, 135.0), Point(580.0, 275.0),
                Point(445.0, 350.0), Point(345.0, 250.0),
            )),
            polygon("Czworokąt", listOf(
                Point(650.0, 150.0), Point(810.0, 150.0), Point(810.0, 315.0),
                Point(650.0, 315.0),
            )),
        ),
        nextId = next,
    )
    val edgeId = base.polygons[1].edges[1].id
    val arcId = base.polygons[2].edges[2].id
    val edge = base.edgeGeometry(edgeId)!!
    val curved = base.copy(
        polygons = base.polygons.map { polygon ->
            polygon.copy(edges = polygon.edges.map { current ->
                if (current.id == edgeId) current.copy(shape = EdgeShape.CubicBezier(
                    edge.firstControl + Vector(-15.0, 70.0),
                    edge.secondControl + Vector(35.0, -55.0),
                )) else if (current.id == arcId) current.copy(shape = EdgeShape.CircularArc()) else current
            })
        },
        continuity = mapOf(
            edge.startVertexId to ContinuitySetting(Continuity.G0, true),
            edge.endVertexId to ContinuitySetting(Continuity.G0, true),
            base.polygons[2].vertices[2].id to ContinuitySetting(Continuity.G0, false),
            base.polygons[2].vertices[3].id to ContinuitySetting(Continuity.G0, false),
        ),
    )
    if (!withConstraints) return curved
    val top = curved.polygons[2].edges[0].id
    val right = curved.polygons[2].edges[1].id
    return curved.copy(
        constraints = listOf(
            LengthConstraint(curved.nextId, top, curved.edgeGeometry(top)!!.length),
            PerpendicularConstraint(curved.nextId + 1, top, right),
        ),
        nextId = curved.nextId + 2,
    )
}
