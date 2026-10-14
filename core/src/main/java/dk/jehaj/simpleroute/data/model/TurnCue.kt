package dk.jehaj.simpleroute.data.model

data class TurnCue(
    val turn: TurnType,
    val turnAngle: Float,
    val offset: Int,
    val description: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val distanceFromStartMeters: Double = 0.0
)
