package dk.jehaj.simpleroute.data.model

enum class ManeuverCategory {
    LEFT,
    RIGHT,
    ROUNDABOUT,
    U_TURN,
    STRAIGHT,
    UNKNOWN
}

enum class TurnType(val code: String, val category: ManeuverCategory) {
    TL("TL", ManeuverCategory.LEFT),       // Turn Left
    TSLL("TSLL", ManeuverCategory.LEFT),   // Slight Left
    TSHL("TSHL", ManeuverCategory.LEFT),   // Sharp Left
    TR("TR", ManeuverCategory.RIGHT),      // Turn Right
    TSLR("TSLR", ManeuverCategory.RIGHT),  // Slight Right
    TSHR("TSHR", ManeuverCategory.RIGHT),  // Sharp Right
    RNDB("RNDB", ManeuverCategory.ROUNDABOUT), // Roundabout
    TU("TU", ManeuverCategory.U_TURN),     // U-Turn
    C("C", ManeuverCategory.STRAIGHT),     // Straight / Continue
    UNKNOWN("", ManeuverCategory.UNKNOWN);

    val isLeft: Boolean get() = category == ManeuverCategory.LEFT
    val isRight: Boolean get() = category == ManeuverCategory.RIGHT
    val isRoundabout: Boolean get() = category == ManeuverCategory.ROUNDABOUT
    val isUTurn: Boolean get() = category == ManeuverCategory.U_TURN

    /**
     * Vibration timing pattern in milliseconds (Off / On alternating).
     * Based on SPEC Section 4:
     * - Turn Left: Single long, weighted pulse [0, 500]
     * - Turn Right: Two crisp, rapid tap pulses [0, 150, 100, 150]
     * - Roundabout: Distinct rolling 3-pulse cadence [0, 100, 80, 100, 80, 250]
     * - U-Turn: Rapid alarm flutter [0, 80, 50, 80, 50, 80]
     */
    val hapticPattern: LongArray
        get() = when (category) {
            ManeuverCategory.LEFT -> longArrayOf(0, 500)
            ManeuverCategory.RIGHT -> longArrayOf(0, 150, 100, 150)
            ManeuverCategory.ROUNDABOUT -> longArrayOf(0, 100, 80, 100, 80, 250)
            ManeuverCategory.U_TURN -> longArrayOf(0, 80, 50, 80, 50, 80)
            ManeuverCategory.STRAIGHT, ManeuverCategory.UNKNOWN -> longArrayOf()
        }

    val defaultAngleDegrees: Float
        get() = when (this) {
            TL -> -90f
            TSLL -> -45f
            TSHL -> -135f
            TR -> 90f
            TSLR -> 45f
            TSHR -> 135f
            TU -> 180f
            RNDB -> 0f
            C, UNKNOWN -> 0f
        }

    companion object {
        fun fromCode(code: String?): TurnType {
            if (code == null) return UNKNOWN
            val trimmed = code.trim().uppercase()
            return entries.firstOrNull { it.code == trimmed } ?: UNKNOWN
        }
    }
}
