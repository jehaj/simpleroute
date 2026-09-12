package dk.jehaj.simpleroute.data.parser

import dk.jehaj.simpleroute.data.model.Route
import dk.jehaj.simpleroute.data.model.TrackPoint
import dk.jehaj.simpleroute.data.model.TurnCue
import dk.jehaj.simpleroute.data.model.TurnType
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import kotlin.math.abs

class GpxParser {

    fun parse(inputStream: InputStream, fileName: String = "route.gpx"): Route {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            // Defend against XXE and XML entity expansion (Billion Laughs) attacks
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            runCatching {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            runCatching {
                setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
        }
        val saxParser = factory.newSAXParser()
        val handler = GpxHandler(fileName)
        saxParser.parse(inputStream, handler)
        return handler.buildRoute()
    }

    private class GpxHandler(private val fileName: String) : DefaultHandler() {
        var routeName: String = fileName.removeSuffix(".gpx")

        private val rawTrackPoints = mutableListOf<RawTrackPoint>()
        private val rawTurnCues = mutableListOf<RawTurnCue>()

        private val textBuffer = StringBuilder()

        private var insideRtept = false
        private var insideTrkpt = false
        private var insideTrk = false
        private var insideWpt = false
        private var insideExtensions = false

        private var currentRteptLat: Double? = null
        private var currentRteptLon: Double? = null
        private var currentDesc: String? = null
        private var currentTurn: String? = null
        private var currentTurnAngle: Float? = null
        private var currentOffset: Int? = null

        private var currentTrkptLat: Double? = null
        private var currentTrkptLon: Double? = null
        private var currentEle: Double = 0.0

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes?
        ) {
            textBuffer.setLength(0)
            val tag = (qName ?: localName ?: "").lowercase()

            when (tag) {
                "trk" -> insideTrk = true
                "wpt" -> insideWpt = true
                "rtept" -> {
                    insideRtept = true
                    currentRteptLat = attributes?.getValue("lat")?.toDoubleOrNull()
                    currentRteptLon = attributes?.getValue("lon")?.toDoubleOrNull()
                    currentDesc = null
                    currentTurn = null
                    currentTurnAngle = null
                    currentOffset = null
                }
                "trkpt" -> {
                    insideTrkpt = true
                    currentTrkptLat = attributes?.getValue("lat")?.toDoubleOrNull()
                    currentTrkptLon = attributes?.getValue("lon")?.toDoubleOrNull()
                    currentEle = 0.0
                }
                "extensions" -> {
                    insideExtensions = true
                }
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (ch != null) {
                val remaining = MAX_TEXT_LENGTH - textBuffer.length
                if (remaining > 0) {
                    val toAppend = kotlin.math.min(length, remaining)
                    textBuffer.append(ch, start, toAppend)
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val tag = (qName ?: localName ?: "").lowercase()
            val text = textBuffer.toString().trim()

            when (tag) {
                "trk" -> insideTrk = false
                "wpt" -> insideWpt = false
                "name" -> {
                    if (insideTrk && text.isNotEmpty()) {
                        routeName = text
                    } else if (!insideRtept && !insideTrkpt && !insideWpt && routeName == fileName.removeSuffix(".gpx")) {
                        if (text.isNotEmpty()) {
                            routeName = text
                        }
                    }
                }
                "desc" -> {
                    if (insideRtept) {
                        currentDesc = text
                    }
                }
                "turn" -> {
                    if (insideRtept) {
                        currentTurn = text
                    }
                }
                "turn-angle" -> {
                    if (insideRtept) {
                        currentTurnAngle = text.toFloatOrNull()
                    }
                }
                "offset" -> {
                    if (insideRtept) {
                        currentOffset = text.toIntOrNull()
                    }
                }
                "ele" -> {
                    if (insideTrkpt) {
                        currentEle = text.toDoubleOrNull() ?: 0.0
                    }
                }
                "rtept" -> {
                    insideRtept = false
                    if (currentOffset != null) {
                        val turnType = TurnType.fromCode(currentTurn)
                        val angle = currentTurnAngle ?: turnType.defaultAngleDegrees
                        rawTurnCues.add(
                            RawTurnCue(
                                turn = turnType,
                                turnAngle = angle,
                                offset = currentOffset!!,
                                desc = currentDesc ?: "",
                                lat = currentRteptLat ?: 0.0,
                                lon = currentRteptLon ?: 0.0
                            )
                        )
                    }
                }
                "trkpt" -> {
                    insideTrkpt = false
                    if (currentTrkptLat != null && currentTrkptLon != null) {
                        rawTrackPoints.add(
                            RawTrackPoint(
                                lat = currentTrkptLat!!,
                                lon = currentTrkptLon!!,
                                ele = currentEle
                            )
                        )
                    }
                }
                "extensions" -> {
                    insideExtensions = false
                }
            }
        }

        fun buildRoute(): Route {
            val processedTrackPoints = ArrayList<TrackPoint>(rawTrackPoints.size)
            var cumulativeDistance = 0.0
            var totalAscent = 0.0
            var totalDescent = 0.0
            var minEle = if (rawTrackPoints.isNotEmpty()) rawTrackPoints[0].ele else 0.0
            var maxEle = minEle

            for (i in rawTrackPoints.indices) {
                val raw = rawTrackPoints[i]
                if (i > 0) {
                    val prev = rawTrackPoints[i - 1]
                    val d = TrackPoint.distanceBetween(prev.lat, prev.lon, raw.lat, raw.lon)
                    cumulativeDistance += d

                    val elevDiff = raw.ele - prev.ele
                    if (elevDiff > 0) {
                        totalAscent += elevDiff
                    } else if (elevDiff < 0) {
                        totalDescent += abs(elevDiff)
                    }
                }

                if (raw.ele < minEle) minEle = raw.ele
                if (raw.ele > maxEle) maxEle = raw.ele

                processedTrackPoints.add(
                    TrackPoint(
                        lat = raw.lat,
                        lon = raw.lon,
                        ele = raw.ele,
                        distanceMeters = cumulativeDistance
                    )
                )
            }

            val processedTurnCues = rawTurnCues
                .sortedBy { it.offset }
                .map { rawCue ->
                    val cueDistance = if (processedTrackPoints.isNotEmpty()) {
                        val clampedOffset = rawCue.offset.coerceIn(0, processedTrackPoints.lastIndex)
                        processedTrackPoints[clampedOffset].distanceMeters
                    } else {
                        0.0
                    }
                    TurnCue(
                        turn = rawCue.turn,
                        turnAngle = rawCue.turnAngle,
                        offset = rawCue.offset,
                        description = rawCue.desc,
                        lat = rawCue.lat,
                        lon = rawCue.lon,
                        distanceFromStartMeters = cueDistance
                    )
                }

            return Route(
                name = routeName,
                fileName = fileName,
                trackPoints = processedTrackPoints,
                turnCues = processedTurnCues,
                totalDistanceMeters = cumulativeDistance,
                totalAscentMeters = totalAscent,
                totalDescentMeters = totalDescent,
                minElevation = minEle,
                maxElevation = maxEle
            )
        }
    }

    private data class RawTrackPoint(val lat: Double, val lon: Double, val ele: Double)
    private data class RawTurnCue(
        val turn: TurnType,
        val turnAngle: Float,
        val offset: Int,
        val desc: String,
        val lat: Double,
        val lon: Double
    )

    companion object {
        private const val MAX_TEXT_LENGTH = 16_384
    }
}
