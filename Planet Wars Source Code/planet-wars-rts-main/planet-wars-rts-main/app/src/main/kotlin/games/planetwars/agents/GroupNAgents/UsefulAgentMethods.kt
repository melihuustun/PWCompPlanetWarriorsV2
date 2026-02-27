package games.planetwars.agents.GroupNAgents

import games.planetwars.core.Planet
import games.planetwars.agents.Action.*
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.PlanetWarsPlayer.*
import games.planetwars.core.GameState.*
import games.planetwars.core.*
import games.planetwars.core.ForwardModel.*

class UsefulAgentMethods {

    // Method takes a target planet and the list of player's owned planets, check if
    // any planet in list has sent a transporter to the target planet, return true/false.7
    // Uses list of own planets to work out yourself, .any is checking that any planet in list meets
    // the inner condition.
    fun playerTransporterTravelling(planet: Planet, myPlanets: List<Planet>): Boolean {
        return (myPlanets.any{it.transporter?.destinationIndex == planet.id })
    }

    //Same as above method but returns the total number of transporters targeting planet.
    //returns the count in the list myPlanets where the condition (counting each planet as variable p)
    // is met, the condition checks p.transporter (? catches and null variables and removes them) and it's
    // destinationIndex against the planet id.
    fun playerTransporterTravellingTotal(planet: Planet, myPlanets: List<Planet>): Int {
        return myPlanets.count { p -> p.transporter?.destinationIndex == planet.id }
    }

    //Same as before for enemy transporters.
    fun enemyTransporterTravelling(planet: Planet, enemyPlanets: List<Planet>): Boolean {
        return (enemyPlanets.any{it.transporter?.destinationIndex == planet.id })
    }
    //Same as before for enemy transporters.
    fun enemyTransporterTravellingTotal(planet: Planet, enemyPlanets: List<Planet>): Int {
        return enemyPlanets.count{p -> p.transporter?.destinationIndex == planet.id}
    }
    //Returns a list of the enemy planets who have sent transporters to the planet we input.
    fun enemyTransporterTravellingFrom(planet: Planet, enemyPlanets: List<Planet>): List<Planet> {
        return enemyPlanets.filter {p -> p.transporter?.destinationIndex == planet.id}
    }

    //Takes the list of enemy planets who have sent transporters, returns a list of the weights of each transporter.
    // Using the map function, we map values from the input list to the ouput one, using the equation/values specified
    // in the {}.
    fun enemyTransporterWeight(enemyTransporterPlanets: List<Planet>): List<Double?> {
        return enemyTransporterPlanets.map{it.transporter?.nShips}
    }

    //Take list of enemy planets who have sent transporters, return the total weight of transporters
    //Targetting the original planet (or whatever the list is conveying).
    // sumOf will sum each element in the list according to the function {}.
    // we call it.transporter? as required since transporter is nullable and ? checks for null values.
    // we then take the nships to be added to the sum. ?: 0.0 means that for each null value, we replace it with 0.0.
    fun enemyTransporterTotalWeight(enemyTransporterPlanets: List<Planet>): Double{
        return enemyTransporterPlanets.sumOf{it.transporter?.nShips ?: 0.0}
    }

    //Collect list of enemy planets in the current game tick.
    //Use gameState to look at all planets in game. Filter for the condition.
    //Condition states if the owner of the planet == player's opponent, keep in list and return when done.
    //fun listOfEnemyPlanets(player: PlanetWarsPlayer, gameState: gameState): List<Planet> {
    //    return gameState.planets.filter{it.owner == player.opponent()}
    //}
    // Integrate this into agents directly, need the gameState and its list of planets in order to work
}