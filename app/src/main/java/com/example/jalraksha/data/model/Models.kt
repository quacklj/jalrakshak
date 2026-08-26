package com.example.jalraksha.data.model

import androidx.annotation.StringRes
import com.example.jalraksha.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One of the eight languages offered on the onboarding screen.
 *
 * [native] is the language's name written in its own script — it stays the same whatever the app
 * is currently set to, which is the whole point of a language picker. [nameRes] is that language's
 * name *in the language being read right now*, so a Hindi reader sees "বাংলা / बांग्ला".
 */
data class Language(
    val code: String,
    val native: String,
    @param:StringRes val nameRes: Int,
) {
    companion object {
        val supported = listOf(
            Language("en", "English", R.string.language_en),
            Language("hi", "हिंदी", R.string.language_hi),
            Language("mr", "मराठी", R.string.language_mr),
            Language("bn", "বাংলা", R.string.language_bn),
            Language("te", "తెలుగు", R.string.language_te),
            Language("ta", "தமிழ்", R.string.language_ta),
            Language("gu", "ગુજરાતી", R.string.language_gu),
            Language("kn", "ಕನ್ನಡ", R.string.language_kn),
        )

        fun byCode(code: String): Language =
            supported.firstOrNull { it.code == code } ?: supported.first()
    }
}

/**
 * A village a user can bind their account to.
 *
 * Place names are the one piece of server data that cannot be reduced to a key, so the backend
 * sends every script it knows in [names] and [districts]. [name] is the Latin spelling and doubles
 * as the fallback for a language nobody has transliterated yet.
 */
@Serializable
data class Village(
    val id: String = "",
    val name: String = "",
    val names: Map<String, String> = emptyMap(),
    val district: String = "",
    val districts: Map<String, String> = emptyMap(),
    val households: Int = 0,
    val sensors: Int = 0,
    /** Latest composite water score, 0–100. */
    val score: Int = 0,
) {
    fun displayName(languageCode: String): String = names[languageCode] ?: name

    fun displayDistrict(languageCode: String): String = districts[languageCode] ?: district

    /**
     * The smaller second line on the village card. It carries the Latin spelling alongside the
     * district when the two differ, because that is the name written on official paperwork — but
     * drops it when the reader is already looking at the Latin name.
     */
    fun secondaryLine(languageCode: String): String {
        val district = displayDistrict(languageCode)
        return if (displayName(languageCode) == name) district else "$name · $district"
    }
}

/**
 * A measured water parameter, rendered as a small card with a progress bar.
 *
 * Every piece of display text is a [key] the app resolves against its own translations. The server
 * sends `"turbidity"`, not `"Turbidity"` — otherwise a Kannada reader would get one English word
 * in the middle of the grid every time the backend spoke first.
 */
@Serializable
data class WaterParameter(
    /** `turbidity`, `chlorine`, `hardness`, `ecoli`, `temperature`. */
    val key: String = "",
    val value: Double = 0.0,
    /** Decimal places to show. Turbidity reads 1.4; an E. coli count reads 0. */
    val decimals: Int = 1,
    @SerialName("unit_key") val unitKey: String = "",
    @SerialName("status_key") val statusKey: String = "",
    /** How full the bar reads, 0–100 — the value against its safe range, not the raw figure. */
    val fill: Int = 0,
    /** Shown only when this app build does not recognise [key]; the server pre-translates it. */
    val label: String? = null,
    /** Shown only when this app build does not recognise [statusKey]. */
    val status: String? = null,
)

/** A parameter shown as a conic ring rather than a bar. */
@Serializable
data class WaterRing(
    /** `ph`, `tds`. */
    val key: String = "",
    val value: Double = 0.0,
    val decimals: Int = 1,
    /** How much of the ring is swept, 0–100. */
    val fill: Int = 0,
    val label: String? = null,
    val note: String? = null,
)

/**
 * Everything the dashboard renders for one village on one day.
 *
 * [score] is the headline number; [verdictKey] and [gradeKey] are the two chips beneath the gauge.
 */
@Serializable
data class WaterReport(
    @SerialName("village_id") val villageId: String = "",
    val score: Int = 0,
    /** `safe`, `boil`, `unsafe`, `unknown`. */
    @SerialName("verdict_key") val verdictKey: String = "",
    /** `crystal_clear`, `good`, `fair`, `poor`. */
    @SerialName("grade_key") val gradeKey: String = "",
    @SerialName("checks_passed") val checksPassed: Int = 0,
    @SerialName("checks_total") val checksTotal: Int = 0,
    /**
     * When the reading was taken. Sent as an instant rather than "2 hours ago" so the app can
     * phrase the freshness itself — and so the phrase stays true if the screen sits open.
     */
    @SerialName("tested_at") val testedAtEpochSeconds: Long = 0,
    val rings: List<WaterRing> = emptyList(),
    val parameters: List<WaterParameter> = emptyList(),
    /** Last seven daily scores, oldest first — drives the sparkline. */
    val history: List<Int> = emptyList(),
    @SerialName("history_delta") val historyDelta: Int = 0,
    /** The dark banner at the bottom of the dashboard, pushed from the central dashboard. */
    val notice: Notice? = null,
)

/**
 * An advisory or scheduled-event banner.
 *
 * Recurring notices carry a [key] and are translated in-app. One-off advisories an officer typed
 * arrive as free text in [title] and [body], already translated by Railway for the language the
 * app asked for.
 */
@Serializable
data class Notice(
    val key: String? = null,
    val title: String = "",
    val body: String = "",
)

/** The signed-in user's profile document. */
@Serializable
data class UserProfile(
    val uid: String = "",
    val phone: String = "",
    @SerialName("village_id") val villageId: String? = null,
    @SerialName("language_code") val languageCode: String = "en",
)

// ===== Screen 05 · Trends =====

/** How long a window the trends screen is showing. */
enum class TrendRange(val wire: String) {
    Week("7D"),
    Month("30D"),
    Year("1Y");

    companion object {
        fun fromWire(value: String): TrendRange =
            entries.firstOrNull { it.wire == value } ?: Week
    }
}

/** Everything screen 05 renders for one village over one [TrendRange]. */
@Serializable
data class TrendsReport(
    @SerialName("village_id") val villageId: String = "",
    val range: String = TrendRange.Week.wire,
    @SerialName("average_score") val averageScore: Int = 0,
    val delta: Int = 0,
    val peak: Int = 0,
    val low: Int = 0,
    /** Days in the window where the score fell into the unsafe band. */
    @SerialName("unsafe_days") val unsafeDays: Int = 0,
    /** `improving`, `steady`, `declining` — the chip beside the title. */
    @SerialName("direction_key") val directionKey: String = "",
    /** Scores across the window, oldest first. Drives the big area chart. */
    val series: List<Int> = emptyList(),
    val movers: List<ParameterMovement> = emptyList(),
    @SerialName("monthly_safe_days") val monthlySafeDays: List<MonthlySafeDays> = emptyList(),
)

/** One parameter's recent history, drawn as a row of small bars. */
@Serializable
data class ParameterMovement(
    /** Same vocabulary as [WaterParameter.key]. */
    val key: String = "",
    val value: Double = 0.0,
    val decimals: Int = 1,
    @SerialName("unit_key") val unitKey: String = "",
    /**
     * Set to `steady` when the parameter has not meaningfully moved. When null, [delta] carries a
     * signed number instead — the two are mutually exclusive.
     */
    @SerialName("delta_key") val deltaKey: String? = null,
    val delta: Double = 0.0,
    @SerialName("delta_decimals") val deltaDecimals: Int = 1,
    /** Normalised 0–100 bar heights, oldest first. */
    val series: List<Int> = emptyList(),
)

/** How many days in a month the score stayed in the safe band. */
@Serializable
data class MonthlySafeDays(
    /** 1–12. Rendered with the locale's own month name, so it needs no translation. */
    val month: Int = 1,
    val year: Int = 0,
    val days: Int = 0,
    @SerialName("of_days") val ofDays: Int = 30,
)

// ===== Screen 06 · Report =====

/** What a villager noticed. Keys, not labels — the app owns the wording. */
enum class WaterIssue(val wire: String) {
    Taste("taste"),
    Odour("odour"),
    Colour("colour"),
    NoSupply("no_supply"),
    Illness("illness"),
    Other("other"),
}

/** How bad it is, in the reporter's own judgement. */
enum class ReportSeverity(val wire: String) {
    Minor("minor"),
    Concerning("concerning"),
    Urgent("urgent"),
}

/** A report the user is composing. */
@Serializable
data class ReportDraft(
    @SerialName("issue_key") val issueKey: String = WaterIssue.Taste.wire,
    @SerialName("severity_key") val severityKey: String = ReportSeverity.Concerning.wire,
    val note: String = "",
    @SerialName("source_key") val sourceKey: String = "",
    @SerialName("source_number") val sourceNumber: Int = 0,
    /** Whether the user attached a photo of the water. */
    @SerialName("has_photo") val hasPhoto: Boolean = false,
)

/** A report already filed, shown in the "Your recent reports" list. */
@Serializable
data class PastReport(
    val id: String = "",
    @SerialName("issue_key") val issueKey: String = "",
    /** The reporter's own words. Falls back to the issue label when they left it blank. */
    val note: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("source_key") val sourceKey: String = "",
    @SerialName("source_number") val sourceNumber: Int = 0,
    /** `open`, `resolved`, `closed`. */
    @SerialName("status_key") val statusKey: String = "",
)

// ===== Screen 07 · Profile =====

/** The account, as screen 07 shows it. */
@Serializable
data class Profile(
    val name: String = "",
    /** E.164 as stored; the screen formats it for display. */
    val phone: String = "",
    @SerialName("village_id") val villageId: String = "",
    val verified: Boolean = false,
    @SerialName("reports_filed") val reportsFiled: Int = 0,
    @SerialName("village_score") val villageScore: Int = 0,
    @SerialName("member_since_months") val memberSinceMonths: Int = 0,
    @SerialName("household_size") val householdSize: Int = 0,
    @SerialName("source_key") val sourceKey: String = "",
    @SerialName("source_number") val sourceNumber: Int = 0,
    /** Whether to push and SMS when the score drops into the unsafe band. */
    @SerialName("unsafe_alerts") val unsafeAlerts: Boolean = true,
) {
    /**
     * Up to two initials for the avatar. Takes the first letter of the first and last words, which
     * works across the scripts the app ships — a Devanagari name yields Devanagari initials.
     */
    val initials: String
        get() {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                words.isEmpty() -> ""
                words.size == 1 -> words[0].take(1)
                else -> words.first().take(1) + words.last().take(1)
            }.uppercase()
        }
}
