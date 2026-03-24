package games.planetwars.agents.GroupNAgents

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.*

class DefensiveReactiveAgent03() : PlanetWarsPlayer() { //New agent defined as subclass of PlanetWarsPlayer()
    private val halfwayLine = params.width / 2 //Get the halfway line of the game space to determine which side we are defending

    override fun getAction(gameState: GameState): Action { //Override getAction from original class, pass gameState in and return action.
        /*
        v0.3 - Updated attacking strategy. Will prioritise attacking the nearest enemy with low planets
        via a heuristic calculation. Also created new methods for help with optimising ships sent.

        This agent was built based off of the Defensive Random Agent.
        This agent is made to choose the source as the planet with the most ships out of the ones owned.
        The target is to be determined by multiple arguments:
            - We first go through all neutral planets on our side and capture any remaining.
            - We then go through a list of all of our owned planets and find if any of the planets on our side are going to be taken over by the enemy
              (judged by net transporter weights being sent to planet stored in a HashMap).
            - We will target it with optimal amount of ships. Otherwise, we will attack.
            - We attack an enemy planet already on our side, or, choose a target enemy on the other side.
            - The enemy on our side is attacked via a search for the minimum distance between the source and planet.
            - The enemy on the other side is attacked via a search for the planet with the lowest heuristic score
              which is based on distance and net ships for the planet.

        Agent issues:
            - All attack the same neutral planet too much. (Need to alter neutral planet targeting so stop attacking once enough is sent).
            - All attack the same enemy planets too much. (Fixed)
            - Need to account better for travel time. (Calculate potential ship gain for target based on time to reach and growthRate)
            - Need to optimise attacking, will change the source in order to do this. Source choice based on distance to target + ships.
            - Need to capture neutral planets at start against aggresive planets.
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
        var target: Planet
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
            val target = myNeutralPlanets.minBy{it.position.distance(source.position) / it.growthRate}
            return Action(player, source.id, target.id, getShipsToSend((target.nShips+1), source))
        }


        // No attacks to defend or neutral to capture, now choose to attack enemy planets.

        // Filter for enemies on our side.
        val enemyOnPlayerSide = halfPlanets.filter{it.owner == player.opponent()}

        // If there are enemies on our side, prioritise their capture to stop enemies taking our planets.
        if (! enemyOnPlayerSide.isEmpty()) {
            //There is at least one enemy on our side, attack enemy planet based on distance
            target = enemyOnPlayerSide.minBy { it.position.distance(source.position) }
            // If we can take the enemy planet in one attack, optimally take it, else, send half of total ships.
            return Action(player, source.id, target.id, getShipsToSend(target.nShips + 1, source))
        }

        // Final decision is to attack as we are (in the current tick) as safe as we can be.

        // If no enemies on our side, attack nearest enemy planet on other side.
        // Can create method to get list of capturable enemy planets based on the source we have.
        // Use the hashmap.

        // Filter through all planets that are enemies. Want to track for minimal value via heuristic
        // Want to target smallest nShips not being captured already while accounting for distance in targeting.
        // filter for p in enemy planets.
        // calc incomingNet correctly and if the net is negative, target if less than minimum found.
        target = otherHalfPlanets[0]
        var targetNet: Double = netShips.getOrElse(target.id) {0.0} - target.nShips
        var targetDist: Double = target.position.distance(source.position)
        for (p in otherHalfPlanets.slice(1..enemyPlanets.lastIndex)) {
            val incomingNet = netShips.getOrElse(p.id) {0.0} - p.nShips
            val checkDist = p.position.distance(source.position)

            // Want to store the heuristically best target. This will be based on the net ships of the
            // current planet and the distance from the source to that planet, we want to minimise both values.
            if (incomingNet < 0 && getHeuristicValue(incomingNet, checkDist) < getHeuristicValue(targetNet, targetDist)) {
                target = p
                targetNet = incomingNet
                targetDist = checkDist
            }
        }
        // Needed amount of ships to send should be the positive targetNet + 1
        val ships: Double = getShipsToSend(target.nShips+3, source)
        if (ships == source.nShips/2) {
            // We want to only attack the enemy if we can safely capture. So, if we can't capture here, we will either do nothing or reinforce another planet.
            // Want to reinforce ships that are ahead of this one.
            return Action.doNothing()
        }
        return Action(player, source.id, target.id, getShipsToSend((target.nShips+1), source))
        // Heuristics: distance, nShips, growthRate.
        // Could calculate estimated planet ships total with time to get to target * growthRate?

    }

    // Repeated check for ships to send turned into a method.
    fun getShipsToSend(needed: Double, source: Planet): Double {
        if (source.nShips >= needed) {
            return needed
        }
        return source.nShips / 2
    }

    // Calculate a score based on the input values.
    fun getHeuristicValue(netShips: Double, distance: Double): Double {
        return netShips + (distance * 0.5)
    }

    override fun getAgentType(): String {
        return "Defensive Random Agent 2.0"
    }

}

    fun main() {
    val agent = DefensiveReactiveAgent()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}