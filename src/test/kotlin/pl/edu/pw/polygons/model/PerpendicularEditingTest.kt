package pl.edu.pw.polygons.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class PerpendicularEditingTest {
    private fun adjacentScene() = Scene(
        polygons = listOf(Polygon(1, "Prostopadłe", listOf(
            Vertex(2, Point(0.0, 0.0)), Vertex(3, Point(10.0, 0.0)),
            Vertex(4, Point(10.0, 10.0)), Vertex(5, Point(0.0, 10.0)),
        ), listOf(Edge(6), Edge(7), Edge(8), Edge(9)))),
        nextId = 10,
    )

    @Test
    fun `either adjacent edge can drive rotation in either selection order`() {
        for ((first, second) in listOf(6L to 7L, 7L to 6L)) {
            val base = adjacentScene().addPerpendicularConstraint(first, second).applied()
            for ((vertexId, drivingEdge) in listOf(2L to 6L, 4L to 7L)) {
                val point = base.polygons.single().vertices.first { it.id == vertexId }.point
                val delta = Vector(2.0, -3.0)
                val moved = base.tryMoveVertex(vertexId, delta).applied()
                assertPoint(point + delta, moved.polygons.single().vertices.first { it.id == vertexId }.point)
                assertPerpendicular(moved)
                val followingEdge = if (drivingEdge == 6L) 7L else 6L
                val oldDirection = base.edgeGeometry(followingEdge)!!.direction.normalized()
                val newDirection = moved.edgeGeometry(followingEdge)!!.direction.normalized()
                assertTrue(oldDirection.dot(newDirection) < 0.999, "The partner edge should rotate")
            }
        }
    }

    @Test
    fun `dragging any endpoint of separate edges preserves the mouse position`() {
        val base = separateScene(listOf(
            Point(0.0, 0.0) to Point(10.0, 0.0),
            Point(30.0, 0.0) to Point(37.0, 9.0),
        ))
        val first = base.polygons[0].edges[0].id
        val second = base.polygons[1].edges[0].id
        val orderA = base.addPerpendicularConstraint(first, second).applied()
        val orderB = base.addPerpendicularConstraint(second, first).applied()
        // Creation also uses a deterministic fallback, not selection order.
        assertEquals(orderA.polygons, orderB.polygons)
        for (polygonIndex in 0..1) for (endpoint in 0..1) {
            val vertex = orderA.polygons[polygonIndex].vertices[endpoint]
            val delta = Vector(2.0, 3.0)
            val movedA = orderA.tryMoveVertex(vertex.id, delta).applied()
            val movedB = orderB.tryMoveVertex(vertex.id, delta).applied()
            assertPoint(vertex.point + delta, movedA.polygons[polygonIndex].vertices[endpoint].point)
            assertEquals(movedA.polygons, movedB.polygons)
            assertPerpendicular(movedA)
        }
    }

    @Test
    fun `the shared vertex remains under the mouse`() {
        val base = adjacentScene().addPerpendicularConstraint(7, 6).applied()
        val moved = base.tryMoveVertex(3, Vector(3.0, 2.0)).applied()
        assertPoint(Point(13.0, 2.0), moved.polygons.single().vertices[1].point)
        assertPerpendicular(moved)
    }

    @Test
    fun `either edge can rotate with both lengths constrained`() {
        for ((first, second) in listOf(6L to 7L, 7L to 6L)) {
            val base = adjacentScene().addPerpendicularConstraint(first, second).applied()
                .addLengthConstraint(6, 10.0).applied()
                .addLengthConstraint(7, 10.0).applied()
            for (vertexId in listOf(2L, 3L, 4L)) {
                val vertex = base.polygons.single().vertices.first { it.id == vertexId }
                val moved = base.tryMoveVertex(vertexId, Vector(2.0, -3.0)).applied()
                assertPoint(vertex.point + Vector(2.0, -3.0), moved.polygons.single().vertices.first { it.id == vertexId }.point)
                assertEquals(10.0, moved.edgeGeometry(6)!!.length, 1e-5)
                assertEquals(10.0, moved.edgeGeometry(7)!!.length, 1e-5)
                assertPerpendicular(moved)
            }
        }
    }

    @Test
    fun `a length change can be driven by either edge`() {
        val base = adjacentScene().addPerpendicularConstraint(6, 7).applied()
        for (edgeId in listOf(6L, 7L)) {
            val changed = base.addLengthConstraint(edgeId, 15.0).applied()
            assertEquals(15.0, changed.edgeGeometry(edgeId)!!.length, 1e-5)
            assertPerpendicular(changed)
            assertEquals(1.0, base.edgeGeometry(edgeId)!!.direction.normalized()
                .dot(changed.edgeGeometry(edgeId)!!.direction.normalized()), 1e-5)
        }
    }

    @Test
    fun `whole polygon translation remains rigid when both edges are edited`() {
        val base = adjacentScene().addLengthConstraint(6, 10.0).applied()
            .addLengthConstraint(7, 10.0).applied().addPerpendicularConstraint(6, 7).applied()
        val delta = Vector(7.0, -4.0)
        val moved = base.tryMovePolygon(1, delta).applied()
        base.polygons.single().vertices.zip(moved.polygons.single().vertices).forEach { (a, b) ->
            assertPoint(a.point + delta, b.point)
        }
        assertPerpendicular(moved)
    }

    @Test
    fun `interaction priority propagates backwards through a perpendicular chain`() {
        val base = separateScene(listOf(
            Point(0.0, 0.0) to Point(10.0, 0.0),
            Point(30.0, 0.0) to Point(30.0, 10.0),
            Point(50.0, 0.0) to Point(60.0, 0.0),
            Point(80.0, 0.0) to Point(80.0, 10.0),
        ))
        val edges = base.polygons.map { it.edges.first().id }
        var constrained = base
        edges.zipWithNext().forEach { (a, b) -> constrained = constrained.addPerpendicularConstraint(a, b).applied() }
        val endpoint = constrained.polygons.last().vertices[1]
        val moved = constrained.tryMoveVertex(endpoint.id, Vector(4.0, -2.0)).applied()
        assertPoint(Point(84.0, 8.0), moved.polygons.last().vertices[1].point)
        assertPerpendicular(moved)
        assertTrue(kotlin.math.abs(moved.edgeGeometry(edges.first())!!.direction.normalized().y) > 0.1)
    }

    @Test
    fun `a curved edge can drive its partner while retaining its fixed length`() {
        val base = separateScene(listOf(
            Point(0.0, 0.0) to Point(10.0, 0.0),
            Point(30.0, 0.0) to Point(30.0, 10.0),
        ))
        val first = base.polygons[0].edges.first().id
        val second = base.polygons[1].edges.first().id
        for (bezier in listOf(false, true)) {
            var curved = if (bezier) base.toggleBezier(second).applied() else base.toggleArc(second).applied()
            val geometry = curved.edgeGeometry(second)!!
            curved = curved.setContinuity(geometry.startVertexId, Continuity.G0).applied()
                .setContinuity(geometry.endVertexId, Continuity.G0).applied()
                .addPerpendicularConstraint(first, second).applied()
            val length = curved.edgeGeometry(second)!!.length
            val constrained = curved.addLengthConstraint(second, length).applied()
            val oldEnd = constrained.edgeGeometry(second)!!.end
            val moved = constrained.tryMoveVertex(geometry.endVertexId, Vector(3.0, -2.0)).applied()
            assertPoint(oldEnd + Vector(3.0, -2.0), moved.edgeGeometry(second)!!.end)
            assertEquals(length, moved.edgeGeometry(second)!!.length, 1e-5)
            assertPerpendicular(moved)
        }
    }

    @Test
    fun `an indirect star relation cannot move the anchor of the driving edge`() {
        val base = demoScene(withConstraints = false).addPerpendicularConstraint(11, 8).applied()
            .addPerpendicularConstraint(8, 10).applied()
        val source = base.edgeGeometry(11)!!
        val moved = base.tryMoveVertex(source.endVertexId, Vector(1.0, 0.5)).applied()

        assertPoint(source.end + Vector(1.0, 0.5), moved.edgeGeometry(11)!!.end)
        assertPoint(source.start, moved.edgeGeometry(11)!!.start)
        assertPerpendicular(moved)
    }

    @Test
    fun `the reported star relations remain stable during back and forth dragging`() {
        val base = demoScene(withConstraints = false).addPerpendicularConstraint(11, 8).applied()
            .addPerpendicularConstraint(8, 10).applied()
        for (vertex in base.polygons.first().vertices) {
            var moved = base
            repeat(100) { step ->
                for (delta in listOf(Vector(1.0, 0.5), Vector(-1.0, -0.5))) {
                    val before = moved.polygons.first().vertices.first { it.id == vertex.id }.point
                    moved = when (val result = moved.tryMoveVertex(vertex.id, delta)) {
                        is ConstraintUpdate.Applied -> result.scene
                        is ConstraintUpdate.Rejected -> fail("Vertex ${vertex.id}, step $step, delta $delta: ${result.reason}")
                    }
                    assertPoint(before + delta, moved.polygons.first().vertices.first { it.id == vertex.id }.point)
                    assertPerpendicular(moved)
                }
            }
            // Tiny repeated drags must not feed back into a large expansion of the star.
            base.polygons.first().vertices.zip(moved.polygons.first().vertices).forEach { (original, result) ->
                assertTrue(original.point.distanceTo(result.point) < 1.0, "Unexpected drift at vertex ${result.id}")
            }
            val translated = moved.tryMovePolygon(base.polygons.first().id, Vector(20.0, 15.0)).applied()
            moved.polygons.first().vertices.zip(translated.polygons.first().vertices).forEach { (a, b) ->
                assertPoint(a.point + Vector(20.0, 15.0), b.point)
            }
        }
    }

    private fun separateScene(segments: List<Pair<Point, Point>>): Scene {
        var next = 1L
        val polygons = segments.map { (start, end) ->
            val id = next++
            val vertices = listOf(Vertex(next++, start), Vertex(next++, end), Vertex(next++, end + Vector(5.0, 7.0)))
            Polygon(id, "Krawędź $id", vertices, List(3) { Edge(next++) })
        }
        return Scene(polygons, next)
    }

    private fun assertPerpendicular(scene: Scene) {
        scene.constraints.filterIsInstance<PerpendicularConstraint>().forEach { relation ->
            val a = scene.edgeGeometry(relation.firstEdgeId)!!.direction.normalized()
            val b = scene.edgeGeometry(relation.secondEdgeId)!!.direction.normalized()
            assertEquals(0.0, a.dot(b), 1e-5)
        }
    }

    private fun assertPoint(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, 1e-8)
        assertEquals(expected.y, actual.y, 1e-8)
    }

    private fun ConstraintUpdate.applied(): Scene = when (this) {
        is ConstraintUpdate.Applied -> scene
        is ConstraintUpdate.Rejected -> fail(reason)
    }
}
