package games.planetwars.agents.GroupNAgents

import games.planetwars.core.Planet
import games.planetwars.agents.Action.*
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.PlanetWarsPlayer.*
import games.planetwars.core.GameState.*
import games.planetwars.core.*
import games.planetwars.core.ForwardModel.*
import games.planetwars.core.GameParams.*

/* class UsefulAgentMethods {

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

    //Returns a list of the players planets who have sent transporters to the planet we input.
    fun playerTransporterTravellingFrom(planet: Planet, myPlanets: List<Planet>): List<Planet> {
        return myPlanets.filter {p -> p.transporter?.destinationIndex == planet.id}
    }

    //Takes the list of enemy planets who have sent transporters, returns a list of the weights of each transporter.
    // Using the map function, we map values from the input list to the ouput one, using the equation/values specified
    // in the {}.
    fun playerTransporterWeight(playerTransporterPlanets: List<Planet>): List<Double?> {
        return playerTransporterPlanets.map{it.transporter?.nShips}
    }

    //Take list of enemy planets who have sent transporters, return the total weight of transporters
    //Targetting the original planet (or whatever the list is conveying).
    // sumOf will sum each element in the list according to the function {}.
    // we call it.transporter? as required since transporter is nullable and ? checks for null values.
    // we then take the nships to be added to the sum. ?: 0.0 means that for each null value, we replace it with 0.0.
    fun playerTransporterTotalWeight(playerTransporterPlanets: List<Planet>): Double{
        return playerTransporterPlanets.sumOf{it.transporter?.nShips ?: 0.0}
    }

    //Takes total weights of enemys and player transporters. Will minus enemy transporters from players to get net value on planet.
    fun netPlanetTransporters(enemyTransporterPlanets: List<Planet>, myPlanets: List<Planet>): Double{
        return (playerTransporterTotalWeight(myPlanets) - playerTransporterTotalWeight(enemyTransporterPlanets))
    }

    //Following method needs to be implemented within a subclass of a PlanetWarsAgent in order to access player variable.
    //Takes all planets in game, the player of focus and the planet we want to target. We find all enemyPlanets, all player Planets.
    //Compute amount to be the net weight between the players transporters going to the target (+) and the enemy transporters going to target (-)
    // Return amount if it is less than or equal 0, in which we return a positive version of the amount + 1. Can alter the +1 later.
    // Else we return 0 and dont want to send.
    fun amountToSend(planets: List<Planet>, player1: PlanetWarsPlayer, target: Planet): Double{
        var enemyPlanets = planets.filter {it.owner == player1.player.opponent()}
        var myPlanets = planets.filter {it.owner == player1.player}
        var amount = 0.0
        amount += netPlanetTransporters(playerTransporterTravellingFrom(target, enemyPlanets), playerTransporterTravellingFrom(target, myPlanets))
        if (amount <= 0) {
            return (amount*-1)+1
        }
        return 0.0
    }

    // Returns the time until the transporter launched reaches the target planet (NOT TESTED)
    // we pass the target planet and the planet the transporter is being sent from into the method and want to return time as Double.
    // We start by making the variable transporter in order to prevent null values in calculations.
    // velocity is stored in transporters as .v and we pull this. Distance is the transporter's position - the targetPlanets position.
    // We calculate euclideanDistance using the appropriate formula for distance. We use the built in magnitude function on the velocity to get the speed.
    // We run a check for possibly getting speed == 0.0 before using it in division. Then calculate time with t = d/s and return.

    fun timeToTarget(targetPlanet: Planet, transporterPlanet: Planet): Double {
        val transporter = transporterPlanet.transporter ?: return 0.0
        var params = GameParams()
        var velocity = transporter.v
        var distance = (transporter.s - targetPlanet.position)
        //Need euclidean distance
        var euclideanDistance = Math.sqrt(Math.pow(distance.x, 2.0) + Math.pow(distance.y, 2.0))
        var speed = velocity.mag()

        if (speed == 0.0) {
            return 0.0
        }

        var time = euclideanDistance / speed
        return time

    }
} */