package pl.edu.pw.polygons.model

sealed interface GeometricConstraint {
    val id: Long
    fun references(edgeId: Long): Boolean
}

data class LengthConstraint(
    override val id: Long,
    val edgeId: Long,
    val desiredLength: Double,
) : GeometricConstraint {
    override fun references(edgeId: Long) = this.edgeId == edgeId
}

data class PerpendicularConstraint(
    override val id: Long,
    val firstEdgeId: Long,
    val secondEdgeId: Long,
) : GeometricConstraint {
    override fun references(edgeId: Long) = firstEdgeId == edgeId || secondEdgeId == edgeId
}

sealed interface ConstraintUpdate {
    data class Applied(val scene: Scene) : ConstraintUpdate
    data class Rejected(val reason: String) : ConstraintUpdate
}
