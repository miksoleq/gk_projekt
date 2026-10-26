package pl.edu.pw.polygons.model

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CircularArcTest {
    private fun scene() = Scene(
        polygons = listOf(Polygon(
            id = 1, name = "Łuk",
            vertices = listOf(
                Vertex(2, Point(100.0, 140.0)), Vertex(3, Point(220.0, 140.0)),
                Vertex(4, Point(260.0, 240.0)), Vertex(5, Point(60.0, 240.0)),
            ),
            edges = listOf(Edge(6), Edge(7), Edge(8), Edge(9)),
        )),
        nextId = 10,
    )

    @Test
    fun `g0 at both ends makes a semicircle on the edge diameter`() {
        val scene = scene().toggleArc(6).applied()
        val edge = scene.edgeGeometry(6)!!
        val arc = edge.arc!!
        assertEquals(Continuity.G0, scene.continuityAt(2))
        assertEquals(Continuity.G0, scene.continuityAt(3))
        assertEquals(Point(160.0, 140.0), arc.center)
        assertEquals(60.0, arc.radius, 1e-8)
        assertEquals(60.0 * PI, edge.length, 1e-8)
        assertEquals(edge.start, edge.pointAt(0.0))
        assertEquals(edge.end, edge.pointAt(1.0))
        for (step in 0..10) assertEquals(arc.radius, edge.pointAt(step / 10.0).distanceTo(arc.center), 1e-8)
    }

    @Test
    fun `g1 uses the directed tangent at either end and follows neighbor movement`() {
        for (vertex in listOf(2L, 3L)) {
            val scene = scene().toggleArc(6).applied().setContinuity(vertex, Continuity.G1).applied()
            assertJoint(scene, vertex)
            val neighborVertex = if (vertex == 2L) 5L else 4L
            val moved = scene.tryMoveVertex(neighborVertex, Vector(30.0, -20.0)).applied()
            assertJoint(moved, vertex)
            assertNotEquals(scene.edgeGeometry(6)!!.arc!!.sweep, moved.edgeGeometry(6)!!.arc!!.sweep)
        }
    }

    @Test
    fun `second g1 and c1 are rejected and g0 allows switching ends`() {
        val scene = scene().toggleArc(6).applied().setContinuity(2, Continuity.G1).applied()
        assertFalse(scene.canSetContinuity(3, Continuity.G1))
        assertIs<ConstraintUpdate.Rejected>(scene.setContinuity(3, Continuity.G1))
        assertIs<ConstraintUpdate.Rejected>(scene.setContinuity(3, Continuity.C1))
        assertEquals(listOf(Continuity.G0, Continuity.G1), scene.availableContinuities(3))
        val switched = scene.setContinuity(2, Continuity.G0).applied().setContinuity(3, Continuity.G1).applied()
        assertJoint(switched, 3)
        val semicircle = switched.setContinuity(3, Continuity.G0).applied().edgeGeometry(6)!!
        assertEquals(semicircle.chordLength / 2.0, semicircle.arc!!.radius, 1e-8)
    }

    @Test
    fun `bezier handle drives the tangent of the neighboring arc`() {
        val scene = scene().toggleArc(6).applied().toggleBezier(7).applied()
            .setContinuity(3, Continuity.G1).applied()
        val original = scene.edgeGeometry(7)!!
        val moved = scene.tryMoveControlPoint(7, 0, Vector(20.0, 5.0)).applied()
        assertEquals(original.firstControl + Vector(20.0, 5.0), moved.edgeGeometry(7)!!.firstControl)
        assertEquals(original.start, moved.edgeGeometry(7)!!.start)
        assertEquals(original.end, moved.edgeGeometry(7)!!.end)
        assertJoint(moved, 3)
    }

    @Test
    fun `arc and polygon can move and arc length constraint survives a drag`() {
        val base = scene().toggleArc(6).applied()
        val translated = base.tryMovePolygon(1, Vector(23.0, -11.0)).applied()
        assertEquals(base.edgeGeometry(6)!!.arc!!.center + Vector(23.0, -11.0), translated.edgeGeometry(6)!!.arc!!.center)
        val constrained = base.addLengthConstraint(6, 200.0).applied()
        assertEquals(200.0, constrained.edgeGeometry(6)!!.length, 1e-5)
        val moved = constrained.tryMoveVertex(3, Vector(10.0, 15.0)).applied()
        assertEquals(200.0, moved.edgeGeometry(6)!!.length, 1e-5)
        assertEquals(moved.edgeGeometry(6)!!.chordLength / 2.0, moved.edgeGeometry(6)!!.arc!!.radius, 1e-8)
    }

    @Test
    fun `perpendicularity uses the chord and can coexist with an arc length`() {
        val scene = scene().toggleArc(6).applied().addLengthConstraint(6, 200.0).applied()
            .addPerpendicularConstraint(8, 6).applied()
        val arc = scene.edgeGeometry(6)!!
        assertEquals(0.0, arc.direction.normalized().dot(scene.edgeGeometry(8)!!.direction.normalized()), 1e-5)
        assertEquals(200.0, arc.length, 1e-5)
    }

    @Test
    fun `converting types keeps the edge identity and rejects invalid arc endpoint relations`() {
        val base = scene()
        val arc = base.toggleArc(6).applied()
        val line = arc.toggleArc(6).applied()
        assertEquals(base.edgeGeometry(6), line.edgeGeometry(6))
        val bezier = arc.toggleBezier(6).applied()
        assertTrue(bezier.edgeGeometry(6)!!.isBezier)
        val arcAgain = bezier.toggleArc(6).applied()
        assertTrue(arcAgain.edgeGeometry(6)!!.isArc)
        assertEquals(Continuity.G0, arcAgain.continuityAt(2))
        val bothG1 = base.toggleBezier(6).applied()
            .setContinuity(2, Continuity.G1).applied().setContinuity(3, Continuity.G1).applied()
        assertIs<ConstraintUpdate.Rejected>(bothG1.toggleArc(6))
    }

    @Test
    fun `splitting an arc gives two arcs and clears constraints on the old edge`() {
        val base = scene().toggleArc(6).applied().addLengthConstraint(6, 200.0).applied()
        val midpoint = base.edgeGeometry(6)!!.pointAt(0.5)
        val split = base.insertAtEdgeMidpoint(6)
        assertEquals(5, split.polygons.single().vertices.size)
        assertTrue(split.constraints.isEmpty())
        val newVertex = split.polygons.single().vertices[1]
        val secondId = split.polygons.single().edges[1].id
        assertEquals(midpoint, newVertex.point)
        assertTrue(split.edgeGeometry(6)!!.isArc)
        assertTrue(split.edgeGeometry(secondId)!!.isArc)
        assertEquals(Continuity.G1, split.continuityAt(newVertex.id))
        assertEquals(base.edgeGeometry(6)!!.length, split.edgeGeometry(6)!!.length + split.edgeGeometry(secondId)!!.length, 1e-5)
        assertJoint(split, newVertex.id)
    }

    @Test
    fun `removing an arc endpoint rechecks circle geometry and removes related constraints`() {
        val base = scene().toggleArc(6).applied().addLengthConstraint(6, 200.0).applied()
        val removed = base.removeVertex(3)
        assertEquals(3, removed.polygons.single().vertices.size)
        assertTrue(removed.constraints.isEmpty())
        val arc = removed.edgeGeometry(6)!!
        assertEquals(arc.chordLength / 2.0, arc.arc!!.radius, 1e-8)
    }

    @Test
    fun `inside test follows the arc and includes the area beyond its chord`() {
        val base = scene()
        val curved = base.toggleArc(6).applied()
        val point = Point(160.0, 100.0)
        assertFalse(containsPoint(base.polygons.single(), point))
        assertTrue(containsPoint(curved.polygons.single(), point))
    }

    @Test
    fun `parallel tangent and coincident endpoints are rejected without changing the scene`() {
        val base = scene()
        val flat = base.tryMoveVertex(5, Vector(-20.0, -100.0)).applied().toggleArc(6).applied()
        assertIs<ConstraintUpdate.Rejected>(flat.setContinuity(2, Continuity.G1))
        assertEquals(Continuity.G0, flat.continuityAt(2))
        val arc = base.toggleArc(6).applied()
        assertIs<ConstraintUpdate.Rejected>(arc.tryMoveVertex(3, Vector(-120.0, 0.0)))
        assertEquals(Point(220.0, 140.0), arc.edgeGeometry(6)!!.end)
    }

    @Test
    fun `arc length can coexist with g1 at either end`() {
        for (vertex in listOf(2L, 3L)) {
            val base = scene().toggleArc(6).applied().setContinuity(vertex, Continuity.G1).applied()
            val constrained = base.addLengthConstraint(6, 220.0).applied()
            assertEquals(220.0, constrained.edgeGeometry(6)!!.length, 1e-5)
            assertJoint(constrained, vertex)
        }
    }

    @Test
    fun `splitting a g1 arc keeps the one g1 per arc rule`() {
        for (vertex in listOf(2L, 3L)) {
            val base = scene().toggleArc(6).applied().setContinuity(vertex, Continuity.G1).applied()
            val split = base.insertAtEdgeMidpoint(6)
            assertEquals(5, split.polygons.single().vertices.size)
            val inserted = split.polygons.single().vertices[1].id
            assertEquals(Continuity.G0, split.continuityAt(inserted))
            assertJoint(split, vertex)
            val g0Half = if (vertex == 2L) split.edgeGeometry(split.polygons.single().edges[1].id)!! else split.edgeGeometry(6)!!
            assertEquals(g0Half.chordLength / 2.0, g0Half.arc!!.radius, 1e-8)
        }
    }

    @Test
    fun `two neighboring arcs can share g1 while other endpoints stay g0`() {
        val joined = scene().toggleArc(6).applied().toggleArc(7).applied()
            .setContinuity(3, Continuity.G1).applied()
        assertJoint(joined, 3)
        assertFalse(joined.canSetContinuity(2, Continuity.G1))
        assertFalse(joined.canSetContinuity(4, Continuity.G1))
    }

    private fun assertJoint(scene: Scene, vertexId: Long) {
        val polygon = scene.polygons.single()
        val index = polygon.vertices.indexOfFirst { it.id == vertexId }
        val incoming = scene.edgeGeometry(polygon.edges[(index - 1 + polygon.edges.size) % polygon.edges.size].id)!!
        val outgoing = scene.edgeGeometry(polygon.edges[index].id)!!
        assertEquals(1.0, incoming.endTangent.normalized().dot(outgoing.startTangent.normalized()), 1e-5)
    }

    private fun ConstraintUpdate.applied(): Scene = when (this) {
        is ConstraintUpdate.Applied -> scene
        is ConstraintUpdate.Rejected -> fail(reason)
    }
}
