package dk.jehaj.simpleroute.data.model

data class Route(
    val name: String,
    val fileName: String,
    val trackPoints: List<TrackPoint>,
    val turnCues: List<TurnCue>,
    val totalDistanceMeters: Double = 0.0,
    val totalAscentMeters: Double = 0.0,
    val totalDescentMeters: Double = 0.0,
    val minElevation: Double = 0.0,
    val maxElevation: Double = 0.0
) {
    val pointCount: Int get() = trackPoints.size
    val cueCount: Int get() = turnCues.size
}
