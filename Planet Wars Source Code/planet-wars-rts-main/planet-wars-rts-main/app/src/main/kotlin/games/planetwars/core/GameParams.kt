package games.planetwars.core

import json_rmi.RemoteConstructable
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class GameParams(
    // spatial parameters
    val width: Int = 640,
    val height: Int = 480,
    val edgeSeparation: Double = 25.0,  // separation between planet and edge of screen: same units as width and height
    val radialSeparation: Double = 1.5,  // separation between planets
    val growthToRadiusFactor: Double = 200.0,

    // game parameters
    //val numPlanets: Int = 10,
    val numPlanets: Int = 20,
    //val initialNeutralRatio: Double = 0.5,
    val initialNeutralRatio: Double = 0.3,
    val maxTicks: Int = 2000,
    val minInitialShipsPerPlanet: Int = 2,
    val maxInitialShipsPerPlanet: Int = 20,
    //val minGrowthRate: Double = 0.02,
    val minGrowthRate: Double = 0.05,
    //val maxGrowthRate: Double = 0.1,
    val maxGrowthRate: Double = 0.2,
    //val transporterSpeed: Double = 3.0,
    val transporterSpeed: Double = 3.5,

    // meta game parameters
    val newMapEachRun: Boolean = true,
) : RemoteConstructable {

}
