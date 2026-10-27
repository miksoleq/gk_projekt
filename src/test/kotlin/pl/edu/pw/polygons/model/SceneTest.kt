package pl.edu.pw.polygons.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class SceneTest {
    @Test
    fun `scene supports multiple polygons and self intersection`() {
        val scene = demoScene(withConstraints = false)
        assertEquals(3, scene.polygons.size)
        assertEquals(5, scene.polygons.first().vertices.size)
        val star = scene.polygons.first()
        val crossing = Point(180.45, 185.0)
        assertTrue(distanceToSegment(crossing, star.edgeStart(0), star.edgeEnd(0)) < 1.0)
        assertTrue(distanceToSegment(crossing, star.edgeStart(2), star.edgeEnd(2)) < 1.0)
    }

    @Test
    fun `star center and tips belong to its interior regardless of vertex order`() {
        val star = demoScene(withConstraints = false).polygons.first()
        val reversed = star.copy(vertices = star.vertices.asReversed())
        for (polygon in listOf(star, reversed)) {
            assertTrue(containsPoint(polygon, Point(180.0, 225.0)))
            assertTrue(containsPoint(polygon, Point(135.0, 125.0)))
            assertFalse(containsPoint(polygon, Point(20.0, 20.0)))
        }
    }

    @Test
    fun `move vertex changes only that vertex`() {
        val scene = demoScene(withConstraints = false)
        val polygon = scene.polygons.first()
        val delta = Vector(12.0, -8.0)
        val moved = scene.moveVertex(polygon.vertices.first().id, delta)
        assertEquals(polygon.vertices.first().point + delta, moved.polygons.first().vertices.first().point)
        assertEquals(polygon.vertices.drop(1), moved.polygons.first().vertices.drop(1))
        assertEquals(scene.polygons.drop(1), moved.polygons.drop(1))
    }

    @Test
    fun `move polygon translates every vertex and leaves other polygons intact`() {
        val scene = demoScene(withConstraints = false)
        val polygon = scene.polygons[1]
        val delta = Vector(9.0, 17.0)
        val moved = scene.movePolygon(polygon.id, delta)
        assertEquals(polygon.vertices.map { it.point + delta }, moved.polygons[1].vertices.map { it.point })
        assertEquals(scene.polygons[0], moved.polygons[0])
        assertEquals(scene.polygons[2], moved.polygons[2])
    }

    @Test
    fun `split inserts midpoint and keeps original edge identity`() {
        val scene = demoScene(withConstraints = false)
        val polygon = scene.polygons[1]
        val edge = polygon.edges.last()
        val start = polygon.vertices.last().point
        val midpoint = start + (polygon.vertices.first().point - start) * 0.5
        val changed = scene.insertAtEdgeMidpoint(edge.id)
        val result = changed.polygons[1]
        assertEquals(polygon.vertices.size + 1, result.vertices.size)
        assertEquals(midpoint, result.vertices.last().point)
        assertEquals(edge.id, result.edges[result.edges.size - 2].id)
        assertEquals(changed.nextId - 2, result.vertices.last().id)
        assertEquals(changed.nextId - 1, result.edges.last().id)
    }

    @Test
    fun `remove vertex reconnects neighboring edges and stops at triangle`() {
        val scene = demoScene(withConstraints = false)
        val polygon = scene.polygons[2]
        val removed = polygon.vertices.first()
        val changed = scene.removeVertex(removed.id)
        val result = changed.polygons[2]
        assertEquals(3, result.vertices.size)
        assertFalse(result.vertices.any { it.id == removed.id })
        assertFalse(result.edges.any { it.id == polygon.edges.first().id })
        assertEquals(polygon.edges.last().id, result.edges.last().id)
        assertSame(changed, changed.removeVertex(result.vertices.first().id))
    }

    @Test
    fun `unknown ids do not mutate scene`() {
        val scene = demoScene(withConstraints = false)
        assertSame(scene, scene.insertAtEdgeMidpoint(-1))
        assertSame(scene, scene.removeVertex(-1))
        assertNotEquals(scene, scene.movePolygon(scene.polygons.first().id, Vector(1.0, 0.0)))
    }

    @Test
    fun `length constraint persists while an endpoint is dragged`() {
        val scene = demoScene(withConstraints = false)
        val edge = scene.polygons.first().edges.first()
        val constrained = scene.addLengthConstraint(edge.id, 180.0).appliedScene()
        val edgeAfterConstraint = constrained.edgeGeometry(edge.id)!!
        val moved = constrained.tryMoveVertex(edgeAfterConstraint.endVertexId, Vector(25.0, 40.0)).appliedScene()

        assertEquals(180.0, moved.edgeGeometry(edge.id)!!.length, 1e-5)
        assertTrue(moved.constraints.single() is LengthConstraint)
    }

    @Test
    fun `perpendicular constraint may join edges from different polygons`() {
        val scene = demoScene(withConstraints = false)
        val firstEdge = scene.polygons[0].edges[0]
        val secondEdge = scene.polygons[1].edges[0]
        val constrained = scene.addPerpendicularConstraint(firstEdge.id, secondEdge.id).appliedScene()
        val first = constrained.edgeGeometry(firstEdge.id)!!
        val second = constrained.edgeGeometry(secondEdge.id)!!

        assertEquals(0.0, first.direction.normalized().dot(second.direction.normalized()), 1e-5)
        assertTrue(constrained.constraints.single() is PerpendicularConstraint)
    }

    @Test
    fun `length and perpendicularity work for adjacent edges in either selection order`() {
        val scene = demoScene(withConstraints = false)
        val polygon = scene.polygons[1]
        val firstEdge = polygon.edges[0]
        val secondEdge = polygon.edges[1]

        val constrained = scene
            .addPerpendicularConstraint(secondEdge.id, firstEdge.id)
            .appliedScene()
            .addLengthConstraint(firstEdge.id, 190.0)
            .appliedScene()

        val first = constrained.edgeGeometry(firstEdge.id)!!
        val second = constrained.edgeGeometry(secondEdge.id)!!
        assertEquals(190.0, first.length, 1e-5)
        assertEquals(0.0, first.direction.normalized().dot(second.direction.normalized()), 1e-5)
        assertEquals(2, constrained.constraints.size)

        val constrainedWithReferenceLength = scene
            .addPerpendicularConstraint(firstEdge.id, secondEdge.id)
            .appliedScene()
            .addLengthConstraint(firstEdge.id, 170.0)
            .appliedScene()
        val reference = constrainedWithReferenceLength.edgeGeometry(firstEdge.id)!!
        val adjusted = constrainedWithReferenceLength.edgeGeometry(secondEdge.id)!!
        assertEquals(170.0, reference.length, 1e-5)
        assertEquals(0.0, reference.direction.normalized().dot(adjusted.direction.normalized()), 1e-5)
    }

    @Test
    fun `editing topology clears constraints that reference changed edges`() {
        val scene = demoScene(withConstraints = false)
        val polygon = scene.polygons[1]
        val edge = polygon.edges.first()
        val constrained = scene.addLengthConstraint(edge.id, 100.0).appliedScene()

        val afterSplit = constrained.insertAtEdgeMidpoint(edge.id)
        assertTrue(afterSplit.constraints.isEmpty())

        val constrainedAgain = scene.addLengthConstraint(edge.id, 100.0).appliedScene()
        val afterRemoval = constrainedAgain.removeVertex(polygon.vertices.first().id)
        assertTrue(afterRemoval.constraints.isEmpty())
    }

    @Test
    fun `constraint can be removed and invalid perpendicularity is rejected`() {
        val scene = demoScene(withConstraints = false)
        val edge = scene.polygons.first().edges.first()
        val constrained = scene.addLengthConstraint(edge.id, 100.0).appliedScene()
        assertNull(constrained.removeConstraint(constrained.constraints.single().id).constraints.singleOrNull())

        val invalid = scene.addPerpendicularConstraint(edge.id, edge.id)
        assertTrue(invalid is ConstraintUpdate.Rejected)
    }

    @Test
    fun `an edge can become a cubic Bezier and its control point can move`() {
        val scene = demoScene(withConstraints = false)
        val edge = scene.polygons[1].edges[3]
        val curve = scene.toggleBezier(edge.id).appliedScene()
        val geometry = curve.edgeGeometry(edge.id)!!

        assertTrue(geometry.isBezier)
        assertEquals(Continuity.C1, curve.continuityAt(geometry.startVertexId))
        assertEquals(Continuity.C1, curve.continuityAt(geometry.endVertexId))

        val editable = curve
            .setContinuity(geometry.startVertexId, Continuity.G0).appliedScene()
            .setContinuity(geometry.endVertexId, Continuity.G0).appliedScene()
        val moved = editable.tryMoveControlPoint(edge.id, 0, Vector(0.0, 45.0)).appliedScene()
        assertEquals(geometry.firstControl + Vector(0.0, 45.0), moved.edgeGeometry(edge.id)!!.firstControl)
        assertTrue(moved.edgeGeometry(edge.id)!!.length > moved.edgeGeometry(edge.id)!!.chordLength)
    }

    @Test
    fun `g1 and c1 propagate tangent changes at a Bezier endpoint`() {
        val scene = demoScene(withConstraints = false)
        val edge = scene.polygons[1].edges[3]
        val curve = scene.toggleBezier(edge.id).appliedScene()
        val start = curve.edgeGeometry(edge.id)!!.startVertexId

        val g1 = curve.setContinuity(start, Continuity.G1).appliedScene()
        val g1Geometry = g1.edgeGeometry(edge.id)!!
        val previousEdge = g1.polygons[1].edges[2]
        val previous = g1.edgeGeometry(previousEdge.id)!!
        assertEquals(1.0, previous.endTangent.normalized().dot(g1Geometry.startTangent.normalized()), 1e-5)

        val c1 = g1.setContinuity(start, Continuity.C1).appliedScene()
        val c1Geometry = c1.edgeGeometry(edge.id)!!
        val c1Previous = c1.edgeGeometry(previousEdge.id)!!
        assertEquals(c1Previous.endTangent, c1Geometry.startTangent)
    }

    @Test
    fun `splitting a Bezier keeps two Bezier segments joined at the curve midpoint`() {
        val scene = demoScene(withConstraints = false)
        val polygon = scene.polygons[1]
        val edge = polygon.edges[1]
        val midpoint = scene.edgeGeometry(edge.id)!!.pointAt(0.5)

        val split = scene.insertAtEdgeMidpoint(edge.id)
        val result = split.polygons[1]
        val firstPart = split.edgeGeometry(edge.id)!!
        val secondPart = split.edgeGeometry(result.edges[2].id)!!

        assertTrue(firstPart.isBezier)
        assertTrue(secondPart.isBezier)
        assertEquals(midpoint, result.vertices[2].point)
        assertEquals(Continuity.C1, split.continuityAt(result.vertices[2].id))
    }

    @Test
    fun `length constraint uses the sampled arc length of a Bezier`() {
        val scene = demoScene(withConstraints = false)
        val curve = scene.polygons[1].edges[1]
        val constrained = scene.addLengthConstraint(curve.id, 210.0).appliedScene()

        assertEquals(210.0, constrained.edgeGeometry(curve.id)!!.length, 1e-5)
    }

    @Test
    fun `dragged control point stays moved at c1 and updates neighboring line`() {
        val scene = demoScene(withConstraints = false)
        val edge = scene.polygons[1].edges[3]
        val curve = scene.toggleBezier(edge.id).appliedScene()
        val before = curve.edgeGeometry(edge.id)!!
        val previousEdge = curve.polygons[1].edges[2]
        val previousStart = curve.edgeGeometry(previousEdge.id)!!.start

        val moved = curve.tryMoveControlPoint(edge.id, 0, Vector(15.0, -30.0)).appliedScene()
        val after = moved.edgeGeometry(edge.id)!!
        val previous = moved.edgeGeometry(previousEdge.id)!!

        assertEquals(before.firstControl + Vector(15.0, -30.0), after.firstControl)
        assertNotEquals(previousStart, previous.start)
        assertEquals(previous.endTangent, after.startTangent)
    }

    private fun ConstraintUpdate.appliedScene(): Scene = when (this) {
        is ConstraintUpdate.Applied -> scene
        is ConstraintUpdate.Rejected -> fail(reason)
    }
}
