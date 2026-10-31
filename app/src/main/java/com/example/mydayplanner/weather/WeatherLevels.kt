package com.example.mydayplanner.weather

/**
 * Source-independent mapping of physical weather values to the watch's levels.
 *
 * Rain answers "How bad could rain get in the NEXT TWO HOURS?", not "How many
 * millimetres accumulate in two hours?". Use the highest short-period intensity,
 * so one violent five-minute shower is not diluted by a dry hour.
 *
 * MOSMIX provides hour-long accumulations (RR1c); the later radar source can
 * supply 5-minute accumulations. Both must use the SAME conversion below.
 *
 * The thresholds are personal/action-oriented, NOT official DWD warning levels.
 * A level 3 is a reason to consider shelter, not necessarily a DWD warning.
 *
 * Wind uses the maximum predicted gust (m/s) in the forecast window.
 */
object WeatherLevels {
    const val rainLookaheadHours = 2L
    const val rainModerateMmPerHour = 1.0
    const val rainHeavyMmPerHour = 7.5
    const val windNoticeableGustMetersPerSecond = 7.0
    const val windStrongGustMetersPerSecond = 11.0
    const val windVeryStrongGustMetersPerSecond = 17.0
    const val thunderPossiblePercent = 20.0

    /** Amount accumulated DURING a period. E.g. 0.75 mm in 5 min = 9 mm/h. */
    data class RainSample(val amountMm: Double?, val durationMinutes: Int)

    /** Peak average rate of any supplied period; null means unavailable, not dry. */
    fun peakRainRateMmPerHour(samples: Iterable<RainSample>): Double? =
        samples.mapNotNull { sample ->
            val amount = sample.amountMm ?: return@mapNotNull null
            if (!amount.isFinite() || amount < 0.0 || sample.durationMinutes <= 0) return@mapNotNull null
            amount * 60.0 / sample.durationMinutes
        }.maxOrNull()

    fun rainFromSamples(samples: Iterable<RainSample>): Int? = rain(peakRainRateMmPerHour(samples))

    /** Argument is an intensity, not an amount accumulated over an arbitrary period. */
    fun rain(mmPerHour: Double?): Int? = mmPerHour
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let {
            when {
                it == 0.0 -> 0
                it < rainModerateMmPerHour -> 1
                it < rainHeavyMmPerHour -> 2
                else -> 3
            }
        }

    fun wind(gustMetersPerSecond: Double?): Int? = gustMetersPerSecond?.let {
        when {
            it < windNoticeableGustMetersPerSecond -> 0
            it < windStrongGustMetersPerSecond -> 1
            it < windVeryStrongGustMetersPerSecond -> 2
            else -> 3
        }
    }

    fun thunder(currentWeatherCode: Int?, probabilityPercent: Double?): Int? =
        when {
            currentWeatherCode != null && currentWeatherCode in 95..99 -> 2
            probabilityPercent != null -> if (probabilityPercent >= thunderPossiblePercent) 1 else 0
            else -> null
        }

    // DWD MOSMIX_L forecast products are normally renewed about four times per day.
    const val forecastMinRefreshMillis = 5L * 60 * 60 * 1000
    const val nearbyStationChangeKm = 15.0
}
