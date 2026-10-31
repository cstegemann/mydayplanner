package com.example.mydayplanner.weather

/**
 * Single source of truth for watch-facing weather levels.
 *
 * Rain is the highest *hourly* forecast rate in the next three hours, in mm/h.
 * Any strictly positive amount is at least level 1; missing data is null, not 0.
 *
 * Wind uses the maximum predicted gust (m/s) over the next three hours.
 * Level 1 deliberately starts at a noticeable breeze, rather than routine light wind.
 * Adjust these boundaries after comparing the face with outdoor conditions.
 */
object WeatherLevels {
    const val rainModerateMmPerHour = 1.0
    const val rainHeavyMmPerHour = 3.0
    const val windNoticeableGustMetersPerSecond = 7.0
    const val windStrongGustMetersPerSecond = 11.0
    const val windVeryStrongGustMetersPerSecond = 17.0
    const val thunderPossiblePercent = 20.0

    fun rain(mmPerHour: Double?): Int? = mmPerHour?.let {
        when {
            it <= 0.0 -> 0
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
