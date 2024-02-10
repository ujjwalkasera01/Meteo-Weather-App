package com.example.meteo.models

import java.io.Serializable

data class Main(
    val temp: Double,
    val feels_like:Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Double,
    val humidity: Int,
    val sea_level: Double,
    val grnd_level: Double
) : Serializable