package games.planetwars.agents.GroupNAgents.DefensiveReactiveAgent

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.*

class DefensiveReactiveAgent11() : PlanetWarsPlayer() { // New agent defined as subclass of PlanetWarsPlayer()

    /*
    v1.1 - Store all distances between planets in a 2d array. Distances are calculated once per game. Source is entirely
    determined via a heuristic when defending. The encoding format has been altered for improved abstraction.

    Desc:
    This agent was loosely based on a Defensive Random Agent, maintaining a similar strategy in gameplay.
    We switch choose a defensive or aggressive action.
    When defending:
        - We go through a list of all of our owned planets and find if any of the planets on our side are going to be taken over by the enemy
        (judged by net transporter weights being sent to each planet stored in a mutable map plus the determined planet growth before
        the selected planet's eventual capture).
        - We will target the planet we find using the best source determined heuristically, sending a strong amount of planets, or, if no source
          is suitable, we move on to attacking.
    When attacking:
        - We will attack from a list of neutral and enemy-owned planets.
        - The target is determined heuristically with a dynamic source selection. The source, target
        pair is selected as the one with the best heuristic value.
    */

    private var distances: Array<DoubleArray>? = null //Declare property distances

    // Constant parameter values for heuristic scores
    private val TOP_SOURCE_RATIO: Double = 0.2
    private val DEFENCE_GROWTH_RATE: Double = 0.7
    private val NEUTRAL_DISTANCE_WEIGHT: Double = 0.8
    private val ENEMY_DISTANCE_WEIGHT: Double = 0.4
    private val SMALL_SOURCE_TICK_DIVISOR: Double = 5.0
    // Data class defines a storage class. This is going to be used to store information on the planets/ships attacking
    // a specific planet.
    private data class IncomingShipsInfo(
        var netShips: Double = 0.0,
        val attackingPlanetIds: MutableList<Int> = mutableListOf()
    )

    // Override the initialisation of the agent in order to reset the distances value for every new game.
    override fun prepareToPlayAs(player: Player, params: GameParams, opponent: String?): String {
        distances = null
        return super.prepareToPlayAs(player, params, opponent)
    }

    // Define the distances between every planet in gameState
    private fun defineDistances(gameState: GameState) {
        val newDistances = Array(gameState.planets.size) { DoubleArray(gameState.planets.size) }
        for (p in gameState.planets) {
            for (q in gameState.planets) {
                if (newDistances[p.id][q.id] != 0.0) continue
                if (p.id == q.id) {
                    newDistances[p.id][q.id]= 0.0
                    newDistances[q.id][p.id] = 0.0
                }
                else {
                    newDistances[p.id][q.id] = p.position.distance(q.position)
                    newDistances[q.id][p.id] = newDistances[p.id][q.id]
                }
            }
        }
        distances = newDistances
    }

    // Get the distance between the two input planets.
    private fun getDist(source: Planet, target: Planet): Double{
        return distances!![source.id][target.id]
    }

    override fun getAction(gameState: GameState): Action { //Override getAction from original class, pass gameState in and return action.
        // Define lists for all types of planets we need to track:
        // myPlanets is all player's planets
        val myPlanets = ArrayList<Planet>(gameState.planets.size)
        // mySourcePlanets is all player's planets that have not fired a transporter
        val mySourcePlanets = ArrayList<Planet>(gameState.planets.size)
        // myAttackingPlanets is all player's planets that have fired a transporter
        val myAttackingPlanets = ArrayList<Planet>(gameState.planets.size)
        // enemyPlanets is all enemy player's planets and neutral planets.
        val enemyPlanets = ArrayList<Planet>(gameState.planets.size)
        // attackingEnemyPlanets is all enemy player's planets that have fired a transporter
        val attackingEnemyPlanets = ArrayList<Planet>(gameState.planets.size)

        // For all planets in the game, decide which lists to put them in based on simple conditions.
        for (p in gameState.planets) {
            if (p.owner == player) {
                myPlanets.add(p)
                if (p.transporter == null) {
                    mySourcePlanets.add(p)
                } else {
                    myAttackingPlanets.add(p)
                }
            } else {
                enemyPlanets.add(p)
                if (p.transporter != null) {
                    attackingEnemyPlanets.add(p)
                }
            }
        }

        // Collect all distances between planets, prevents further calculation in runs.
        if (distances == null || distances!!.size != gameState.planets.size) {
            defineDistances(gameState)
        }


        if (mySourcePlanets.isEmpty()) { //If no possible source planets returned, no action.
            return Action.doNothing()
        }

        // AI suggested inclusion / fix:
        /*
            - Create a hashmap which uses planets as keys and a default 0.0 as values.
            - Define netShips as a hashmap using all planets in gameState. associate by the key = planet.id and the value = it.nShips.
            - Convert to a mutable map which can be altered.
            - From this we can calculate n planets gained during the travel time of the transporter using
              the growthRate, transporter position and transporter speed. */

        // Create a mutable map which uses planet ids as keys and a unique data class IncomingShipsInfo as the values.
        // The data class stores the netShips attacking the planet and the list of planets targeting it. We will use
        // this map to work out what planets are in danger of capture, which are vulnerable, and when exactly their
        // capture will occur.
        val planetInfo =
            gameState.planets.associateBy(keySelector = { it.id }, valueTransform = { IncomingShipsInfo() })
                .toMutableMap()

        //For all of our planets that are attacking
        for (p in myAttackingPlanets) {
            //t is the planet's transporter, check for null and continue if true.
            val t = p.transporter ?: continue
            val destination = t.destinationIndex
            // Need to validate the key 'destination' is in the hashmap before calling it
            if (planetInfo.containsKey(destination)) {
                // Update value at destination id with the ships being sent towards it. Also add the
                // attacking planet to the list of planets targeting this one.
                // ('!!' will force Kotlin to ignore the potential for a null value when calling the destination id.
                // Kotlin normally wouldn't allow this, and it's bad practice, but there is 0 possibility of destination
                // not existing here)
                planetInfo[destination]!!.netShips += t.nShips
                planetInfo[destination]!!.attackingPlanetIds.add(p.id)
            }
        }

        //Same as above for attacking enemy Planets.
        for (p in attackingEnemyPlanets) {
            val t = p.transporter ?: continue
            val destination = t.destinationIndex
            if (planetInfo.containsKey(destination)) {
                planetInfo[destination]!!.netShips -= t.nShips
                planetInfo[destination]!!.attackingPlanetIds.add(p.id)
            }
        }

        // Now choose to update enemy planet details with their calculated growthRate for when they could get captured.
        // Ensures that we can target an enemy planet that will survive a previously sent transporter.
        for (planet in enemyPlanets) {
            if (!planetInfo.getOrElse(planet.id) { IncomingShipsInfo() }.attackingPlanetIds.isEmpty()) {
                if (planet.owner != Player.Neutral) {
                    planetInfo[planet.id]!!.netShips -= (determineShipsGenerated(gameState, planet, planetInfo))
                }
            }
        }
        // Want to look at all planets we own to see if they're about to be captured. Focuses on the defensive move to stop our planets
        // from being captured.
        val defendAction = defendStrategy(myPlanets, planetInfo, gameState, mySourcePlanets)
        // If we have a suitable defensive action, return it.
        if (defendAction != null) {
            return defendAction
        }
        // If there is no suitable defensive action, move toward an attack.
        return attackPlanetStrategy(enemyPlanets, gameState, planetInfo, mySourcePlanets)

    }

    // Full encoding for defending against attacks
    private fun defendStrategy(myPlanets: List<Planet>, planetInfo: MutableMap<Int, IncomingShipsInfo>,gameState: GameState, mySourcePlanets: List<Planet>): Action? {
        //For all of our planets.
        for (planet in myPlanets) {

            // Base value for incomingNet is the sum of our planet's ship count and the net value of ships heading towards it.
            var incomingNet = planet.nShips + planetInfo.getOrElse(planet.id) { IncomingShipsInfo() }.netShips

            // Make further checks if the planet is being attacked.
            if (planetInfo.getOrElse(planet.id) { IncomingShipsInfo() }.attackingPlanetIds.isNotEmpty()) {
                // We are determining how many ships will be created by the planet before it is captured.
                val shipsGenerated = determineShipsGenerated(gameState, planet, planetInfo)
                // Add these to the netShips for future possible calculations and the incomingNet to make sure we only
                // defend this one if absolutely necessary.
                incomingNet += shipsGenerated
                planetInfo[planet.id]!!.netShips += shipsGenerated
            }

            // If incomingNet is positive, do nothing, check next planet.
            if (incomingNet > 0) continue

            // Calculated value needed taking the positive value of the net nShips + 1
            val needed = abs(incomingNet) + 1.0
            val source = getDefendSource(planet, mySourcePlanets, planetInfo)
            if (source == null) {
                continue
            }
            return Action(player, source.id, planet.id, getShipsToSend(needed, source))
        }
        return null
    }

    private fun getDefendSource(target: Planet, sourceList: List<Planet>, planetInfo: MutableMap<Int, IncomingShipsInfo>): Planet? {
        var source: Planet? = null
        // Assign as large value to be replaced
        var score = Double.MAX_VALUE
        for (s in sourceList) {
            if (s.id == target.id || s.transporter != null) continue
            // if score better, use that
            val incomingNet = planetInfo.getOrElse(s.id) {IncomingShipsInfo()}.netShips
            val netShips = s.nShips + incomingNet
            if (netShips <= 0 || s.growthRate <= 0.0) continue
            val sscore: Double = getSourceHeuristicValue(s, target, netShips)
            if (sscore < score) {
                score = sscore
                source = s
            }
        }
        return source
    }

    // Heuristic for choosing a source when defending
    private fun getSourceHeuristicValue(source: Planet, target: Planet, netShips: Double): Double {
        val d = getDist(source, target)
        return d / (netShips * (source.growthRate*DEFENCE_GROWTH_RATE))
    }

    // Full encoding for attacking a planet
    private fun attackPlanetStrategy(enemyPlanets: List<Planet>, gameState: GameState, planetInfo: MutableMap<Int, IncomingShipsInfo>, mySourcePlanets: List<Planet>): Action {

        // Making a list of planets we will want to attack. All enemy planets and neutral planets. Filter for planets that are not already being captured.
        val planetsToAttack = enemyPlanets.filter {
            val targetNet = planetInfo.getOrElse(it.id) {IncomingShipsInfo()}.netShips - it.nShips
            targetNet <= 0
        }

        // If there are no enemies left (final tick), do not return an action.
        if (planetsToAttack.isEmpty()) {
            return Action.doNothing()
        }

        // We want to find a small list of our optimal sources.
        // We use our list of possible source planets, sorted by descending order using the nShips values. Out of this list,
        // we take a dynamic amount, being the size of the full list * a constant. (+1 is used to avoid a value of 0)
        val optimalSources: List<Planet> =
            mySourcePlanets.sortedByDescending { it.nShips }.take(max(1, ((mySourcePlanets.size * TOP_SOURCE_RATIO).toInt() + 1)))

        // Now we collect our action. Returned via the appropriate method which takes our list of sources and targets.
        var act = attackPlanetWithSource(planetsToAttack, optimalSources, planetInfo)
        val source = gameState.planets.get(act.sourcePlanetId)
        // Want to ensure we aren't attacking for no reason. If our attacking is using the default value of
        // source.nShips/2, and this value is a small amount (worked out dynamically), then we shouldn't waste our small
        // planet's ships.
        if (act.numShips == source.nShips / 2) {
            if (source.nShips <= (source.growthRate * (params.maxTicks / SMALL_SOURCE_TICK_DIVISOR))) {
                act = Action.doNothing()
            }
        }
        return act
    }

    // Method for attacking a planet with a list of possible sources.
    // This will choose the best source, target pairing using unique heuristics for each. We then return them
    // in an action.
    private fun attackPlanetWithSource(targetList: List<Planet>, sourceList: List<Planet>, planetInfo: MutableMap<Int, IncomingShipsInfo>): Action {
        // Define our default 'best' source and target to be changed.
        var source: Planet = sourceList[0]
        var target: Planet = targetList[0]
        // Current bestScore is the max possible value stored as a double, to be updated.
        var bestScore = Double.MAX_VALUE

        // Loop through all possible sources
        for (s in sourceList) {
            // Loop through all targets.
            if (s.transporter != null) continue
            for (t in targetList) {
                // Calculate net ships and distance from source to target
                val incomingNet = planetInfo.getOrElse(t.id) {IncomingShipsInfo()}.netShips - t.nShips
                val checkDist = getDist(s,t)

                // Get the heuristic score of the target
                val tScore = getHeuristicValue(incomingNet, checkDist, t)
                // Get a heuristic score of the source via the distance / the nShips
                val sScore = checkDist / s.nShips
                // Combines scores (target weighted more than source)
                val combinedScore = tScore + (sScore * 0.5)
                // If new score is better than the stored one, it is the new best action.
                if (combinedScore < bestScore) {
                    source = s
                    target = t
                    bestScore = combinedScore
                }
            }
        }
        return Action(player, source.id, target.id, getShipsToSend(target.nShips - planetInfo.getOrElse(target.id) { IncomingShipsInfo() }.netShips +1.0, source))
    }

    // Method gets the necessary ships to send, will send the exact number of ships input
    // or the source's total ships / 2.
    private fun getShipsToSend(needed: Double, source: Planet): Double {
        if (source.nShips >= needed) {
            return needed
        }
        return source.nShips / 2
    }

    // Calculates a heuristic score for targeting based on the input values.
    // If the target is an enemy planet, we focus on input netShips and distance,
    // if it is neutral, we also look at the growthRate to see the value of the neutral planet.
    private fun getHeuristicValue(netShips: Double, distance: Double, target:Planet): Double {
        return if (target.owner == Player.Neutral) {
            (netShips + (distance * NEUTRAL_DISTANCE_WEIGHT)) / (target.growthRate+1)
        } else {
            netShips + (distance*ENEMY_DISTANCE_WEIGHT)
        }
    }

    // Calculate the travel time for a given transporter to reach its target.
    // Calculated via the transporter's distance from its target / the transporter speed.
    private fun getTravelTime(gameState: GameState, source: Int, target: Planet): Double {
        return (gameState.planets[source].transporter!!.s.distance(target.position)) / params.transporterSpeed
    }

    // Method to determine at which point in time, using the game's current state and knowable actions, the
    // planet in question will be captured. We then use this to return the total ships the planet being targeted will
    // produce.
    private fun determineShipsGenerated(gameState: GameState, source:Planet, planetInfo: MutableMap<Int, IncomingShipsInfo>): Double {
        // Can store the map for source as a value 'info'
        val info = planetInfo.getOrElse(source.id) {IncomingShipsInfo()}
        if (info.attackingPlanetIds.isEmpty()) return 0.0
        if (info.attackingPlanetIds.size == 1) return getTravelTime(gameState, info.attackingPlanetIds[0], source)*source.growthRate
        // Define a new value 'events' to easily store a map of the time
        // and ships gained/lost from each attack on the source planet.
        val events = info.attackingPlanetIds.map { attackerId ->
            val attacker = gameState.planets[attackerId]
            val t = getTravelTime(gameState, attackerId, source)

            val shipChange = if (attacker.owner == player) {
                attacker.transporter!!.nShips
            } else {
                -attacker.transporter!!.nShips
            }

            Pair(t, shipChange)
        }.sortedBy{it.first}

        //Tracking nShips
        var nShipsStore = source.nShips
        //Tracking time passed between events to generate accurate ships for planet.
        var lastTime = 0.0
        // For each time and nShips in the events storage
        for ((t, x) in events) {
            val newTime = t - lastTime //New amount of time to generate ships
            nShipsStore += newTime * source.growthRate
            //Track total
            nShipsStore += x

            // If our total nShips ever gets below 0, we return the total ships generated.
            if (nShipsStore <= 0) {
                return t*source.growthRate
            }
            // Update recent time.
            lastTime = t
        }
        // If we don't need to reinforce, return the total ships generated in the time frame for decision-making.
        return lastTime*source.growthRate
    }

    override fun getAgentType(): String {
        return "Defensive Reactive Agent 1.1"
    }
}

fun main() {
    val agent = DefensiveReactiveAgent11()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}