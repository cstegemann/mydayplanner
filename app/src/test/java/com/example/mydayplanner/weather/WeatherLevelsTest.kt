package com.example.mydayplanner.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherLevelsTest {
    @Test fun rainStartsAboveZero() {
        assertEquals(0, WeatherLevels.rain(0.0))
        assertEquals(1, WeatherLevels.rain(0.001))
        assertEquals(1, WeatherLevels.rain(0.9))
        assertEquals(2, WeatherLevels.rain(1.0))
        assertEquals(2, WeatherLevels.rain(3.0))
        assertEquals(3, WeatherLevels.rain(7.5))
        assertNull(WeatherLevels.rain(null))
    }

    @Test fun aBriefDownpourIsNotAveragedOverTwoHours() {
        val samples = listOf(
            WeatherLevels.RainSample(0.0, 5),
            WeatherLevels.RainSample(0.75, 5),
            WeatherLevels.RainSample(0.0, 5)
        )
        assertEquals(9.0, WeatherLevels.peakRainRateMmPerHour(samples)!!, 0.001)
        assertEquals(3, WeatherLevels.rainFromSamples(samples))
        assertEquals(1, WeatherLevels.rainFromSamples(listOf(WeatherLevels.RainSample(0.001, 60))))
        assertNull(WeatherLevels.rainFromSamples(listOf(WeatherLevels.RainSample(null, 5))))
    }

    @Test fun routineBreezeIsNotWindLevelOne() {
        assertEquals(0, WeatherLevels.wind(5.0))
        assertEquals(0, WeatherLevels.wind(6.99))
        assertEquals(1, WeatherLevels.wind(7.0))
        assertEquals(2, WeatherLevels.wind(11.0))
        assertEquals(3, WeatherLevels.wind(17.0))
        assertNull(WeatherLevels.wind(null))
    }

    @Test fun thunderUsesCentralProbability() {
        assertEquals(0, WeatherLevels.thunder(null, 19.0))
        assertEquals(1, WeatherLevels.thunder(null, 20.0))
        assertEquals(2, WeatherLevels.thunder(95, 0.0))
        assertNull(WeatherLevels.thunder(null, null))
    }
}
