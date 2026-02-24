package games.planetwars.agents.GroupNAgents

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.*

class DefensiveRandomAgent() : PlanetWarsPlayer() { //New agent defined as subclass of planetwarsplayer()

    override fun getAction(gameState: GameState): Action { //Override getAction from original class, pass gameState in and return action.

        //var myPlanets: List<Planet>

        //if (gameState.gameTick > 500) {
            //myPlanets = gameState.planets.filter{ it.owner == player && it.transporter == null && it.nShips > 5 } //Identify planets owned by player or no-one
        //} else {
            //myPlanets = gameState.planets.filter{ it.owner == player && it.transporter == null } //Identify planets owned by player or no-one
        //}

        val myPlanets = gameState.planets.filter{ it.owner == player && it.transporter == null} //Identify planets owned by player or no-one

        if (myPlanets.isEmpty()) { //If no planets returned, no action
            return Action.doNothing()
        }
        val source = myPlanets.random() //Random selection source from list of owned planets
        val halfwayLine = params.width / 2 //Get the halfway line of the game space to determine which side we are defending

        var halfPlanets: List<Planet> //Choose halfPlanets, declared as variable to store List of Planets
        var target: Planet //Declare variable to store the target planet which we will set based on the following decisions

        if (player == Player.Player1) { //If Player1, choose from planets on left side.
            halfPlanets = gameState.planets.filter { it.position.x <= halfwayLine && (it.owner == player.opponent() || it.owner == Player.Neutral) && it != source}
            //Filter for planets on left half of screen where the agent owns it or is neutral
        }
        else { //If Player2, choose from planets on right side
            halfPlanets = gameState.planets.filter { it.position.x >= halfwayLine && (it.owner == player.opponent() || it.owner == Player.Neutral) && it != source }
            //Filter for planets on right half of screen where the agent owns it or is neutral
        }

        // If no planets are returned (meaning no opponent or neutral planets left on our side)
        // Then attack closest planet on other half
        // As this only runs after the checks on our side, it means these planets will be ones on the opposing side of the halfway line
        if (halfPlanets.isEmpty()) {
            val opposingPlanets = gameState.planets.filter{it.owner == player.opponent() || it.owner == Player.Neutral}
            // Filter for planets that belong to opponent or are neutral.
            if (opposingPlanets.isEmpty()){
                return Action.doNothing() // if there are no planets that belong to opponent or neutral, do nothing
            }
            val closestOpposingPlanet = opposingPlanets.minBy{distanceBetween(source, it)}
            // return the planet that gives the smallest distance between itself and the source,
            // i.e. the closest planet on the other half that isn't ours
            target = closestOpposingPlanet


        } else {
            target = halfPlanets.random() // Randomly choose target from selected list of planets on our side
        }

        return Action(player, source.id, target.id, source.nShips/2)
        //Return action command with appropriate collected information
    }

    fun distanceBetween(planet1: Planet, planet2: Planet): Double {
        return sqrt((planet1.position.x - planet2.position.x).pow(2) + (planet1.position.y - planet2.position.y).pow(2))
    }

    override fun getAgentType(): String {
        return "Defensive Random Agent"
    }

}
fun main() {
    val agent = DefensiveRandomAgent()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}