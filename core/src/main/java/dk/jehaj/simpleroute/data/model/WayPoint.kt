package dk.jehaj.simpleroute.data.model

data class WayPoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val type: String? = null,
    val ele: Double = 0.0,
    val desc: String? = null
) {
    val isStart: Boolean
        get() = type.equals("from", ignoreCase = true) ||
                name.equals("from", ignoreCase = true) ||
                name.equals("start", ignoreCase = true)

    val isEnd: Boolean
        get() = type.equals("to", ignoreCase = true) ||
                name.equals("to", ignoreCase = true) ||
                name.equals("end", ignoreCase = true) ||
                name.equals("goal", ignoreCase = true) ||
                name.equals("finish", ignoreCase = true)
}
