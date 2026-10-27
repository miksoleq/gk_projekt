package pl.edu.pw.polygons.model

import pl.edu.pw.polygons.interaction.DragGesture
import pl.edu.pw.polygons.interaction.DragTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class InteractionTest {
    @Test
    fun `free Bezier handles follow the pointer without moving their endpoints`() {
        val scene = demoScene(withConstraints = false)
        val edge = scene.edgeGeometry(scene.polygons[1].edges[1].id)!!
        for (index in 0..1) {
            val old = if (index == 0) edge.firstControl else edge.secondControl
            val gesture = DragGesture.begin(scene, DragTarget.Control(edge.edgeId, index), old)!!
            var updated = scene
            for (step in 1..20) {
                val target = old + Vector(step * 1.5, step * -0.5)
                updated = gesture.move(updated, target).applied()
                val result = updated.edgeGeometry(edge.edgeId)!!
                assertPoint(target, if (index == 0) result.firstControl else result.secondControl)
                assertEquals(edge.start, result.start)
                assertEquals(edge.end, result.end)
            }
        }
    }

    @Test
    fun `G1 and C1 preserve the dragged handle and its endpoints while a neighboring line follows`() {
        val base = demoScene(withConstraints = false)
        val edgeId = base.polygons[1].edges[3].id
        val curve = base.toggleBezier(edgeId).applied()
        for (index in 0..1) for (type in listOf(Continuity.G1, Continuity.C1)) {
            val geometry = curve.edgeGeometry(edgeId)!!
            val vertex = if (index == 0) geometry.startVertexId else geometry.endVertexId
            val scene = curve.setContinuity(geometry.startVertexId, Continuity.G0).applied()
                .setContinuity(geometry.endVertexId, Continuity.G0).applied().setContinuity(vertex, type).applied()
            val before = scene.edgeGeometry(edgeId)!!
            val oldControl = if (index == 0) before.firstControl else before.secondControl
            val target = oldControl + Vector(12.0, 18.0)
            val updated = scene.tryMoveControlPoint(edgeId, index, target - oldControl).applied()
            val after = updated.edgeGeometry(edgeId)!!
            assertPoint(target, if (index == 0) after.firstControl else after.secondControl)
            assertEquals(before.start, after.start)
            assertEquals(before.end, after.end)
            assertNotEquals(scene.polygons, updated.polygons)
            val polygon = updated.polygons[1]
            val neighborId = polygon.edges[if (index == 0) 2 else 4].id
            val neighbor = updated.edgeGeometry(neighborId)!!
            val a = if (index == 0) neighbor.endTangent else after.endTangent
            val b = if (index == 0) after.startTangent else neighbor.startTangent
            assertEquals(1.0, a.normalized().dot(b.normalized()), 1e-5)
            if (type == Continuity.C1) assertTrue((a - b).length() < 1e-5)
        }
    }

    @Test
    fun `either handle at a joint between Beziers is the driver at C1`() {
        val scene = Scene(listOf(Polygon(1, "Krzywe", listOf(
            Vertex(2, Point(0.0, 0.0)), Vertex(3, Point(120.0, 0.0)),
            Vertex(4, Point(120.0, 120.0)), Vertex(5, Point(0.0, 120.0)),
        ), listOf(Edge(6), Edge(7), Edge(8), Edge(9)))), 10)
            .toggleBezier(6).applied().toggleBezier(7).applied()
        for ((edgeId, index) in listOf(6L to 1, 7L to 0)) {
            val edge = scene.edgeGeometry(edgeId)!!
            val old = if (index == 0) edge.firstControl else edge.secondControl
            val target = old + Vector(9.0, 7.0)
            val updated = scene.tryMoveControlPoint(edgeId, index, target - old).applied()
            val after = updated.edgeGeometry(edgeId)!!
            assertPoint(target, if (index == 0) after.firstControl else after.secondControl)
            assertEquals(edge.start, after.start)
            assertEquals(edge.end, after.end)
            assertTrue((updated.edgeGeometry(6)!!.endTangent - updated.edgeGeometry(7)!!.startTangent).length() < 1e-5)
        }
    }

    @Test
    fun `fixed Bezier length may move endpoints but keeps the dragged handle fixed`() {
        val base = demoScene(withConstraints = false)
        val edgeId = base.polygons[1].edges[1].id
        val length = base.edgeGeometry(edgeId)!!.length
        val scene = base.addLengthConstraint(edgeId, length).applied()
        for (index in 0..1) {
            val before = scene.edgeGeometry(edgeId)!!
            val old = if (index == 0) before.firstControl else before.secondControl
            val target = old + Vector(40.0, 55.0)
            val updated = scene.tryMoveControlPoint(edgeId, index, target - old).applied()
            val after = updated.edgeGeometry(edgeId)!!
            assertPoint(target, if (index == 0) after.firstControl else after.secondControl)
            assertEquals(length, after.length, 1e-5)
            assertTrue(before.start != after.start || before.end != after.end)
        }
    }

    @Test
    fun `absolute cursor targets retain grab offset and recover after a rejected motion`() {
        val scene = demoScene(withConstraints = false)
        val vertex = scene.polygons[0].vertices[0]
        val offset = Vector(2.0, 3.0)
        val gesture = DragGesture.begin(scene, DragTarget.Vertex(vertex.id), vertex.point + offset)!!
        val acceptedPointer = vertex.point + offset + Vector(30.0, 10.0)
        val updated = gesture.move(scene, acceptedPointer).applied()
        assertPoint(vertex.point + Vector(30.0, 10.0), updated.polygons[0].vertices[0].point)
        val same = gesture.move(updated, acceptedPointer).applied()
        assertEquals(updated.polygons, same.polygons)

        val arc = Scene(listOf(Polygon(1, "Łuk", listOf(
            Vertex(2, Point(100.0, 140.0)), Vertex(3, Point(220.0, 140.0)),
            Vertex(4, Point(260.0, 240.0)), Vertex(5, Point(60.0, 240.0)),
        ), listOf(Edge(6), Edge(7), Edge(8), Edge(9)))), 10)
            .toggleArc(6).applied().setContinuity(2, Continuity.G1).applied()
        val arcGesture = DragGesture.begin(arc, DragTarget.Vertex(5), Point(60.0, 240.0))!!
        // This would make the G1 tangent parallel to the chord: no finite circle exists.
        assertIs<ConstraintUpdate.Rejected>(arcGesture.move(arc, Point(60.0, 140.0)))
        val recovered = arcGesture.move(arc, Point(90.0, 220.0)).applied()
        assertPoint(Point(90.0, 220.0), recovered.polygons.single().vertices.last().point)
    }

    @Test
    fun `startup relations are satisfied and the curved examples remain editable`() {
        val scene = demoScene()
        assertTrue(scene.polygons.size >= 2)
        assertTrue(scene.polygons.any { p -> p.edges.any { it.shape is EdgeShape.CubicBezier } })
        assertTrue(scene.polygons.any { p -> p.edges.any { it.shape is EdgeShape.CircularArc } })
        val length = scene.constraints.filterIsInstance<LengthConstraint>().single()
        assertEquals(160.0, length.desiredLength)
        assertEquals(length.desiredLength, scene.edgeGeometry(length.edgeId)!!.length, 1e-5)
        val relation = scene.constraints.filterIsInstance<PerpendicularConstraint>().single()
        assertEquals(0.0, scene.edgeGeometry(relation.firstEdgeId)!!.direction.normalized()
            .dot(scene.edgeGeometry(relation.secondEdgeId)!!.direction.normalized()), 1e-5)
        val translated = scene.tryMovePolygon(scene.polygons[2].id, Vector(3.0, 5.0)).applied()
        scene.polygons[2].vertices.zip(translated.polygons[2].vertices).forEach { (a, b) ->
            assertPoint(a.point + Vector(3.0, 5.0), b.point)
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
