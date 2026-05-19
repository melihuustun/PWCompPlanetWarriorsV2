package games.planetwars.agents.GroupNAgents.DefensiveReactiveAgent

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.*

class DefensiveReactiveAgent04() : PlanetWarsPlayer() { //New agent defined as subclass of PlanetWarsPlayer()
    private val halfwayLine = params.width / 2 //Get the halfway line of the game space to determine which side we are defending

    override fun getAction(gameState: GameState): Action { //Override getAction from original class, pass gameState in and return action.
        /*
        v0.4 - Created the attackPlanet function to make decision-making the same for all scenarios where we attack
        planets directly. Changed the targeting for attacking an enemy on our side to use this function. Added new heuristic
        for attacking neutral planets that focuses on growth rate and distance and utilised it in the same attackPlanet function.

        This agent was built based off of the Defensive Random Agent, it is made to choose the source as the
        planet with the most ships out of the ones owned.
        The target is to be determined by multiple arguments:
            - We go through a list of all of our owned planets and find if any of the planets on our side are going to be taken over by the enemy
              (judged by net transporter weights being sent to planet stored in a HashMap).
            - We will target it with optimal amount of ships. Otherwise, we will attack.
            - We will look for any neutral planets on our side of the game, then target one utilising our heuristic
              calculation based around our source.
            - If there are no neutral planets on our side, we then attack an enemy planet already on
              our side, or, choose a target enemy on the other side.
            - The enemy on either side is attacked via a search for the planet with the lowest heuristic score, determined
              by distance and the netShips associated with the target planet.

        Current Agent Issues:
            - Need to account better for travel time. (Calculate potential ship gain for target based on
              time to reach and growthRate)
            - Need to optimise attacking, will change the source in order to do this. Source choice
              based on distance to target + ships. (PRIORITY)
        */



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
            if (onPlayerHalf(p) ) { halfPlanets.add(p)} else { if (p.owner != player) {otherHalfPlanets.add(p)}}
        }


        // Identify owned planets not currently in-action
        var myPlanets = gameState.planets.filter{ it.owner == player && it.transporter == null} //Identify planets owned by player not doing an action

        if (myPlanets.isEmpty()) { //If no planets returned, no action. (Cannot remove.)
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
        // Want to look at all planets we own to see if they're about to be captured. Focuses on the defensive move to stop our planets
        // from being captured.

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
                // Return Action with numShips depending on if the source can send needed.
                return Action(player, source.id, planet.id, getShipsToSend(needed, source))
            }
        }

        // Define neutral planets
        val myNeutralPlanets = halfPlanets.filter{it.owner == Player.Neutral}

        // If there is no attacks to defend, we then check for neutral planets on our side of the field, if there are any neutral planets, we capture.
        if (! myNeutralPlanets.isEmpty()) {
            // Determine which planet to capture by heuristic value, looking for minimal value (determined by low distance,
            // high growth rate and low capture cost). Send optimal ships in action returned.

            // ::getNeutralHeuristicValue is how a method is passed as a parameter. We are using the same method to pass
            // multiple heuristic calculations for targetting.
            return attackPlanet(myNeutralPlanets, source, netShips, ::getNeutralHeuristicValue)
        }


        // No attacks to defend or neutral to capture, we now choose to attack enemy planets.

        // Filter for enemies on our side.
        val enemyOnPlayerSide = halfPlanets.filter{it.owner == player.opponent()}

        // If there are enemies on our side, prioritise their capture to stop enemies taking our planets.
        if (! enemyOnPlayerSide.isEmpty()) {
            // Retrieve the action to commit based on heuristic search for the closest enemy we can capture with our source.
            val action: Action = attackPlanet(enemyOnPlayerSide, source, netShips, ::getEnemyHeuristicValue)
            // Need to check if we are defaulting to the first enemy
            if (action.destinationPlanetId == enemyOnPlayerSide[0].id) {
                val incomingNet = netShips.getOrElse(enemyOnPlayerSide[0].id) {0.0} - enemyOnPlayerSide[0].nShips
                // Error handling, if we have defaulted to the first enemy in our heuristic search, it may be because all
                // current enemies on our side are already being attacked in previous actions, this check is making sure
                // we don't over defend.
                if (incomingNet <= 0) {
                    return action
                }
            }
            else {
                return action
            }
        }

        // Final decision is to attack, as we are (in the current tick) as safe as we can be.

        // If there are no enemies to attack (the game is over), must return action.DoNothing() to prevent error.
        if (otherHalfPlanets.isEmpty()) {
            return Action.doNothing()
        }
        // Retrieve attacking action for enemy planets.
        val action: Action = attackPlanet(otherHalfPlanets, source, netShips, ::getEnemyHeuristicValue)
        // Needed amount of ships to send should be the positive targetNet + 1
        if (action.numShips == source.nShips/2) {
            // We want to only attack the enemy if we can safely capture. So, if we can't capture here, we will do nothing
            // to stay defensive.
            return Action.doNothing()
        }
        return action

    }


    // Generalised method for attacking enemy planets.
    // Find the target with the lowest heuristic value according to its net ships and distance
    // Then attack it with the optimal amount of ships.
    fun attackPlanet(planetList: List<Planet>, source: Planet, netShips: Map<Int, Double>, getHeuristicValue: (Double, Double, Planet) -> Double): Action {
        // Default values to first planet in list in order to compare and store a minimum heuristic value.
        var target: Planet = planetList[0]
        var targetNet: Double = netShips.getOrElse(target.id) {0.0} - target.nShips
        var targetDist: Double = target.position.distance(source.position)
        // For all planets other than the first in the list.
        for (p in planetList.slice(1..planetList.lastIndex)) {
            // Calculate the net value of the planet (-nShips as it is not owned by the player).
            val incomingNet = netShips.getOrElse(p.id) {0.0} - p.nShips
            val checkDist = p.position.distance(source.position)

            // Want to store the heuristically best target. This will be based on the net ships of the
            // current planet and the distance from the source to that planet, we want to minimise both values.
            if (incomingNet <= 0 && getHeuristicValue(incomingNet, checkDist, p) < getHeuristicValue(targetNet, targetDist, target)) {
                target = p
                targetNet = incomingNet
                targetDist = checkDist
            }
        }
        return Action(player, source.id, target.id, getShipsToSend(target.nShips+1, source))
    }

    // Method gets the necessary ships to send, will send the needed amount of ships or the total ships / 2.
    fun getShipsToSend(needed: Double, source: Planet): Double {
        if (source.nShips >= needed) {
            return needed
        }
        return source.nShips / 2
    }

    // Calculate a score for enemy targeting based on the input values (netShips and distance).
    fun getEnemyHeuristicValue(netShips: Double, distance: Double, target: Planet): Double {
        return netShips + (distance * 0.5)
    }

    // Calculate a score for neutral targeting based on the input values (netShips and distance) plus target's growthRate.
    fun getNeutralHeuristicValue(netShips: Double, distance: Double, target: Planet): Double {
        return (target.nShips + (distance * 0.5)) / target.growthRate
    }

    override fun getAgentType(): String {
        return "Defensive Reactive Agent 0.4"
    }

}

    fun main() {
    val agent = DefensiveReactiveAgent04()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}