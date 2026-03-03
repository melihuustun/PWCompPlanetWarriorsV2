package games.planetwars.agents.GroupNAgents

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.*

class DefensiveReactiveAgent() : PlanetWarsPlayer() { //New agent defined as subclass of PlanetWarsPlayer()
    private val halfwayLine = params.width / 2 //Get the halfway line of the game space to determine which side we are defending

    override fun getAction(gameState: GameState): Action { //Override getAction from original class, pass gameState in and return action.
        //
        // This agent has no random chance. The strategy is based the Random DefensiveAgent.
        // It is made to choose the source as the planet with the most ships out of the ones owned.
        // The target is to be determined by multiple arguments.
        // We first go through all neutral planets on our side and capture any remaining,
        // we then go through a list of all of our owned planets and find if any of the planets on our side are going to be taken over by the enemy
        // (judged by net transporter weights being sent to planet stored in a HashMap),
        // we will target it with optimal amount of ships. Otherwise, we will attack
        // an enemy planet already on our side or the nearest enemy planet.
        //
        // v0.2 - Faster initialisation of half and otherHalf planets for use in decision-making
        // Changed net transporter weight storage for all planets to a hashMap system.
        //
        // Idea for improvement - Change to attack enemy planet with heuristic involving nShips and distance if we aren't defending.
        // Also need to optimise nShips sent to the enemy planets too.

        //Define planets on our side. Defined values for half/otherHalf planets as ArrayLists to be altered.
        //Their capacity is the total planets in the game.
        val halfPlanets = ArrayList<Planet> (gameState.planets.size)
        val otherHalfPlanets = ArrayList<Planet>(gameState.planets.size)
        // Define a condition based on a condition. if we are player1, store the condition method onPlayerHalf
        // as position.x <= halfwayLine, otherwise, as position.x >= halfwayLine.
        val onPlayerHalf: (Planet) -> Boolean = if (player == Player.Player1) {
            {it.position.x <= halfwayLine}
        } else {
            {it.position.x >= halfwayLine}
        }
        // For all planets in the game, decide which list to put them in based on our defined condition above.
        // if true, add to halfPlanets, else, add to otherHalfPlanet.
        for (p in gameState.planets) {
            if (onPlayerHalf(p) ) { halfPlanets.add(p)} else {otherHalfPlanets.add(p)}
        }


        // Identify owned planets not currently in-action
        var myPlanets = gameState.planets.filter{ it.owner == player && it.transporter == null} //Identify planets owned by player not doing an action
        if (myPlanets.isEmpty()) { //If no planets returned, no action
            return Action.doNothing()
        }

        // Identify enemy planets
        val enemyPlanets = gameState.planets.filter{it.owner == player.opponent()} //Identify planets owned by enemy


        // Identify enemy planets in-action
        val attackingEnemyPlanets = enemyPlanets.filter{it.transporter != null}

        // Source planet is determined by the one with maximum number of ships.
        val source = myPlanets.maxBy{it.nShips}

        // Remove source from decision-making.
        myPlanets = myPlanets.filter{it != source}

        // Identify our Planets in action.
        val myAttackingPlanets = gameState.planets.filter{it.owner == player && it.transporter != null && it != source}


        // AI suggested inclusion / fix
        // Create a hashmap which uses planets as keys and a default 0.0 as values.
        // Define netShips as a hashmap using all planets in gameState. associate by the key = planet.id and the value = it.nShips.
        // Convert to mutable map which can be altered.
        val netShips = gameState.planets.associateBy(keySelector = {it.id}, valueTransform = {0.0}).toMutableMap()

        //For all of our planets that are attacking
        for (p in myAttackingPlanets) {
            //t is the planet's transporter, check for null and continue.
            val t = p.transporter ?: continue
            val destination = t.destinationIndex
            // Need to validate the key 'destination' is in the hashmap before calling it
            if (netShips.containsKey(destination)) {
                // Update value at destination with the calculated values travelling towards the planet from t.
                netShips[destination] = (netShips[destination] ?: 0.0) + t.nShips
            }
        }
        //Same as above for attacking enemy Planets.
        for (p in attackingEnemyPlanets) {
            val t = p.transporter ?: continue
            val destination = t.destinationIndex
            if (netShips.containsKey(destination))  {
                netShips[destination] = (netShips[destination] ?: 0.0) - t.nShips
            }
        }

        //For all of our planets.
        for (planet in myPlanets) {
            // Net nShips pulled from map 'orElse' = return 0.0 if not found.
            val incomingNet = planet.nShips + netShips.getOrElse(planet.id) {0.0}
            // If incomingNet is positive, do nothing.
            if (incomingNet > 0) continue
            //If incomingNet is negative and enemy will capture, focus the target planet.
            if (incomingNet <= 0) {
                // Calculated value needed taking the positive value of the net nShips + 1
                val needed = abs(incomingNet) + 1.0
                //Return statement changes with condition. If we have the required amount of ships to send, send them, else, send half of total.
                return if (source.nShips > needed) {
                    Action(player, source.id, planet.id, needed)
                } else {
                    Action(player, source.id, planet.id, source.nShips/2)
                }
            }
        }

        val neutralPlanets = halfPlanets.filter{it.owner == Player.Neutral}

        if (! neutralPlanets.isEmpty()) {
            return Action(player, source.id, neutralPlanets.minBy{it.position.distance(source.position)}.id, source.nShips/2)
        }

        // Nothing to actively defend, check if we have enemy planets on our side.

        val enemyOnPlayerSide = halfPlanets.filter{it.owner == player.opponent()}
        //If no enemies on our side, attack nearest enemy planet on other side.
        if (enemyOnPlayerSide.isEmpty()) {
            val target = enemyPlanets.minBy{it.position.distance(source.position)}
            return Action(player, source.id, target.id, source.nShips/2)
        }
        //If enemies on our side, attack nearest enemy to source position thats on our side.
        val target = enemyOnPlayerSide.minBy{it.position.distance(source.position)}
        return Action(player, source.id, target.id, source.nShips/2)


    }

    override fun getAgentType(): String {
        return "Defensive Random Agent 2.0"
    }

}



    fun main() {
    val agent = DefensiveRandomAgent()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}