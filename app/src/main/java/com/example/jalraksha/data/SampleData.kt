package com.example.jalraksha.data

import com.example.jalraksha.data.model.MonthlySafeDays
import com.example.jalraksha.data.model.Notice
import com.example.jalraksha.data.model.ParameterMovement
import com.example.jalraksha.data.model.PastReport
import com.example.jalraksha.data.model.Profile
import com.example.jalraksha.data.model.TrendRange
import com.example.jalraksha.data.model.TrendsReport
import com.example.jalraksha.data.model.Village
import com.example.jalraksha.data.model.WaterParameter
import com.example.jalraksha.data.model.WaterReport
import com.example.jalraksha.data.model.WaterRing

/**
 * The figures drawn in `Jalraksha Mobile.dc.html`, in the shape the real API will send them.
 *
 * These back the @Preview composables and the fake repositories, so the app is fully explorable —
 * in all eight languages — before Firebase and the Railway service exist. Place names carry every
 * script because that is exactly what the backend will have to do.
 */
object SampleData {

    /** Nashik district, in each script the app ships. */
    private val district = mapOf(
        "en" to "Nashik, MH",
        "hi" to "नाशिक, महाराष्ट्र",
        "mr" to "नाशिक, महाराष्ट्र",
        "bn" to "নাসিক, মহারাষ্ট্র",
        "te" to "నాసిక్, మహారాష్ట్ర",
        "ta" to "நாசிக், மகாராஷ்டிரா",
        "gu" to "નાશિક, મહારાષ્ટ્ર",
        "kn" to "ನಾಸಿಕ್, ಮಹಾರಾಷ್ಟ್ರ",
    )

    val villages = listOf(
        Village(
            id = "rampur",
            name = "Rampur Khurd",
            names = mapOf(
                "en" to "Rampur Khurd",
                "hi" to "रामपुर खुर्द",
                "mr" to "रामपूर खुर्द",
                "bn" to "রামপুর খুর্দ",
                "te" to "రాంపూర్ ఖుర్ద్",
                "ta" to "ராம்பூர் குர்த்",
                "gu" to "રામપુર ખુર્દ",
                "kn" to "ರಾಂಪುರ ಖುರ್ದ್",
            ),
            district = "Nashik, MH",
            districts = district,
            households = 412,
            sensors = 6,
            score = 92,
        ),
        Village(
            id = "chandpura",
            name = "Chandpura",
            names = mapOf(
                "en" to "Chandpura",
                "hi" to "चंदपुरा",
                "mr" to "चंदपुरा",
                "bn" to "চাঁদপুরা",
                "te" to "చంద్‌పురా",
                "ta" to "சந்த்பூரா",
                "gu" to "ચંદપુરા",
                "kn" to "ಚಂದಪುರ",
            ),
            district = "Nashik, MH",
            districts = district,
            households = 268,
            sensors = 4,
            score = 78,
        ),
    )

    fun reportFor(villageId: String): WaterReport {
        val village = villages.firstOrNull { it.id == villageId } ?: villages.first()
        return report.copy(villageId = village.id, score = village.score)
    }

    val report: WaterReport
        get() = WaterReport(
            villageId = "rampur",
            score = 92,
            verdictKey = "safe",
            gradeKey = "crystal_clear",
            checksPassed = 9,
            checksTotal = 9,
            // Two hours ago, so the freshness line reads the way the design draws it.
            testedAtEpochSeconds = System.currentTimeMillis() / 1000 - 2 * 60 * 60,
            rings = listOf(
                WaterRing(key = "ph", value = 7.2, decimals = 1, fill = 72),
                WaterRing(key = "tds", value = 320.0, decimals = 0, fill = 64),
            ),
            parameters = listOf(
                WaterParameter("turbidity", 1.4, 1, "ntu", "clear", 22),
                WaterParameter("chlorine", 0.4, 1, "mgl", "within_limit", 40),
                WaterParameter("hardness", 148.0, 0, "mgl", "soft", 48),
                WaterParameter("ecoli", 0.0, 0, "cfu", "not_detected", 4),
            ),
            history = listOf(74, 78, 76, 84, 82, 88, 92),
            historyDelta = 4,
            notice = Notice(key = "tanker_check"),
        )
}

/**
 * Sample payloads for screens 05–07, matching the figures drawn in the design.
 *
 * Kept beside [SampleData] rather than inside it so the dashboard's own sample stays readable.
 */
object SampleTrends {

    fun forRange(range: TrendRange): TrendsReport = when (range) {
        TrendRange.Week -> base.copy(range = range.wire, averageScore = 89, delta = 4)
        TrendRange.Month -> base.copy(
            range = range.wire,
            averageScore = 86,
            delta = 3,
            series = listOf(78, 80, 76, 82, 85, 83, 88, 86, 90, 87, 92, 89),
        )
        TrendRange.Year -> base.copy(
            range = range.wire,
            averageScore = 83,
            delta = 7,
            series = listOf(72, 74, 78, 76, 81, 79, 84, 82, 86, 85, 88, 92),
        )
    }

    private val base: TrendsReport
        get() = TrendsReport(
            villageId = "rampur",
            range = TrendRange.Week.wire,
            averageScore = 89,
            delta = 4,
            peak = 96,
            low = 84,
            unsafeDays = 0,
            directionKey = "improving",
            series = listOf(84, 79, 88, 74, 90, 82, 96),
            movers = listOf(
                ParameterMovement(
                    key = "ph",
                    value = 7.2,
                    decimals = 1,
                    unitKey = "ph",
                    deltaKey = "steady",
                    series = listOf(52, 58, 55, 62, 60, 64, 61, 66, 63, 68, 65, 70),
                ),
                ParameterMovement(
                    key = "tds",
                    value = 320.0,
                    decimals = 0,
                    unitKey = "ppm",
                    delta = -28.0,
                    deltaDecimals = 0,
                    series = listOf(80, 76, 72, 74, 66, 62, 58, 60, 54, 50, 46, 42),
                ),
                ParameterMovement(
                    key = "turbidity",
                    value = 1.4,
                    decimals = 1,
                    unitKey = "ntu",
                    delta = -0.6,
                    deltaDecimals = 1,
                    series = listOf(70, 66, 74, 60, 56, 62, 48, 44, 40, 38, 34, 30),
                ),
                ParameterMovement(
                    key = "chlorine",
                    value = 0.4,
                    decimals = 1,
                    unitKey = "mgl",
                    delta = 0.1,
                    deltaDecimals = 1,
                    series = listOf(30, 34, 32, 40, 44, 42, 50, 54, 52, 58, 62, 66),
                ),
            ),
            monthlySafeDays = listOf(
                MonthlySafeDays(month = 3, days = 26, ofDays = 31),
                MonthlySafeDays(month = 4, days = 28, ofDays = 30),
                MonthlySafeDays(month = 5, days = 24, ofDays = 31),
                MonthlySafeDays(month = 6, days = 29, ofDays = 30),
                MonthlySafeDays(month = 7, days = 30, ofDays = 31),
                MonthlySafeDays(month = 8, days = 26, ofDays = 31),
            ),
        )
}

/** Screens 06 and 07. */
object SampleAccount {

    val profile = Profile(
        name = "Sunita Patil",
        phone = "+919876543210",
        villageId = "rampur",
        verified = true,
        reportsFiled = 5,
        villageScore = 92,
        memberSinceMonths = 18,
        householdSize = 5,
        sourceKey = "handpump",
        sourceNumber = 4,
        unsafeAlerts = true,
    )

    val pastReports: List<PastReport>
        get() {
            val now = System.currentTimeMillis() / 1000
            return listOf(
                PastReport(
                    id = "r-2",
                    issueKey = "colour",
                    note = "Muddy water after rain",
                    createdAt = now - 14 * 24 * 60 * 60,
                    sourceKey = "handpump",
                    sourceNumber = 2,
                    statusKey = "resolved",
                ),
                PastReport(
                    id = "r-1",
                    issueKey = "odour",
                    note = "Chlorine smell",
                    createdAt = now - 29 * 24 * 60 * 60,
                    sourceKey = "tap_line",
                    sourceNumber = 1,
                    statusKey = "closed",
                ),
            )
        }
}
