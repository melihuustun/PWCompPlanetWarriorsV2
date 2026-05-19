package games.planetwars.agents.GroupNAgents.DefensiveReactiveAgent

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.*

class DefensiveReactiveAgent10() : PlanetWarsPlayer() { // New agent defined as subclass of PlanetWarsPlayer()
    private val halfwayLine = params.width / 2 // Get the halfway line of the game space to determine which side we are defending

    // Data class defines a storage class. This is going to be used to store information on the planets/ships attacking
    // a specific planet.
    data class IncomingShipsInfo(
        var netShips: Double = 0.0,
        val attackingPlanetIds: MutableList<Int> = mutableListOf()
    )

    override fun getAction(gameState: GameState): Action { //Override getAction from original class, pass gameState in and return action.
        /*
        v1.0 - Almost all lists of Planet objects get initialised together in one for loop at the start, each planet's
        net ship calculations now include their determined ship production until capture.

        Desc:
        This agent was loosely based on the Defensive Random Agent, maintaining a similar strategy in gameplay.
        The source planet of this agent's actions is chosen as the planet with the greatest number of ships when defending
        and capturing neutral planets. It is chosen dynamically otherwise.
        The target is to be determined by multiple arguments:
            - We go through a list of all of our owned planets and find if any of the planets on our side are going to be taken over by the enemy
              (judged by net transporter weights being sent to each planet stored in a mutable map plus the determined planet growth before
              the selected planet's eventual capture).
            - We will target it with an optimal number of ships. Otherwise, if there is nothing to defend, we will attack.
            - We will look for any neutral planets on our side of the game, then target one utilising our heuristic
              calculation based around our source.
            - If there are no neutral planets on our side, we will then attack one an enemy planet or a neutral planet
              on the enemy's side. The target is determined heuristically with a dynamic source selection. The source, target
              pair is selected as the one with the best heuristic value.
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

        // Define lists for all types of planets we need to track:
        // myPlanets is all player's planets
        val myPlanets = ArrayList<Planet>(gameState.planets.size)
        // mySourcePlanets is all player's planets that have not fired a transporter
        val mySourcePlanets = ArrayList<Planet>(gameState.planets.size)
        // myAttackingPlanets is all player's planets that have fired a transporter
        val myAttackingPlanets = ArrayList<Planet>(gameState.planets.size)
        // myNeutralPlanets is all neutral planets on the player's half
        val myNeutralPlanets = ArrayList<Planet>(gameState.planets.size)
        // enemyPlanets is all enemy player's planets
        val enemyPlanets = ArrayList<Planet>(gameState.planets.size)
        // attackingEnemyPlanets is all enemy player's planets that have fired a transporter
        val attackingEnemyPlanets = ArrayList<Planet>(gameState.planets.size)

        // For all planets in the game, decide which lists to put them in based on our defined condition above and extra
        // simple conditions added below.
        for (p in gameState.planets) {
            if (onPlayerHalf(p) ) {
                halfPlanets.add(p)
                if (p.owner == Player.Neutral) {myNeutralPlanets.add(p)}
            }
            else { if (p.owner != player) {otherHalfPlanets.add(p)}}
            if (p.owner == player) {
                myPlanets.add(p)
                if (p.transporter == null) {mySourcePlanets.add(p)}
                else {myAttackingPlanets.add(p)}
            }
            else { enemyPlanets.add(p)
                if (p.transporter != null) {attackingEnemyPlanets.add(p)}
            }
        }


        if (mySourcePlanets.isEmpty()) { //If no possible source planets returned, no action.
            return Action.doNothing()
        }

        // Source planet is determined by the one with maximum number of ships for defending and capturing neutral.
        val source = mySourcePlanets.maxBy{it.nShips}

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
        val planetInfo = gameState.planets.associateBy(keySelector = {it.id}, valueTransform = {IncomingShipsInfo()}).toMutableMap()

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
            if (planetInfo.containsKey(destination))  {
                planetInfo[destination]!!.netShips -= t.nShips
                planetInfo[destination]!!.attackingPlanetIds.add(p.id)
            }
        }

        // Want to look at all planets we own to see if they're about to be captured. Focuses on the defensive move to stop our planets
        // from being captured.

        // Track if we try and defend
        var triedToDefend = false

        //For all of our planets.
        for (planet in myPlanets) {
            // Remove source from targeting.
            if (planet == source) continue

            // Base value for incomingNet is the sum of our planet's ship count and the net value of ships heading towards it.
            var incomingNet = planet.nShips + planetInfo.getOrElse(planet.id) {IncomingShipsInfo()}.netShips

            // Make further checks if the planet is being attacked.
            if (planetInfo.getOrElse(planet.id) {IncomingShipsInfo()}.attackingPlanetIds.isNotEmpty()) {
                // We are determining how many ships will be created by the planet before it is captured.
                val shipsGenerated = determineCaptureTime(gameState, planet, planetInfo)*planet.growthRate
                // Add these to the netShips for future possible calculations and the incomingNet to make sure we only
                // defend this one if absolutely necessary.
                incomingNet += shipsGenerated
                planetInfo[planet.id]!!.netShips+=shipsGenerated
            }

            // If incomingNet is positive, do nothing, check next planet.
            if (incomingNet > 0) continue

            //If incomingNet is negative and enemy will capture, focus the target planet.
            if (incomingNet <= 0) {
                triedToDefend = true
                // Calculated value needed taking the positive value of the net nShips + 1
                val needed = abs(incomingNet) + 1.0
                // Return Action with numShips depending on if the source can send a suitable amount.
                if (source.nShips < needed/1.5) continue
                return Action(player, source.id, planet.id, getShipsToSend(needed, source))
            }
        }

        // If we failed to defend because our source is too small. Do not try and attack, wait.
        if (triedToDefend) {
            return Action.doNothing()
        }

        // If there are no attacks to defend, we then check for neutral planets on our side of the field, if there are any neutral planets, we capture.
        if (myNeutralPlanets.isNotEmpty()) {
            // Determine which planet to capture by heuristic value, looking for minimal value (determined by low distance,
            // high growth rate and low capture cost). Send optimal ships in action returned.
            return attackPlanet(myNeutralPlanets, source, planetInfo)
        }

        // Now choose to update enemy planet details with their calculated growthRate for when they could get captured.
        // Ensures that we can target an enemy planet that will survive a previously sent transporter.
        for (planet in enemyPlanets) {
            if (! planetInfo.getOrElse(planet.id) {IncomingShipsInfo()}.attackingPlanetIds.isEmpty()) {
               planetInfo[planet.id]!!.netShips-= (determineCaptureTime(gameState, planet, planetInfo))
            }
        }

        // No attacks to defend or neutral to capture, we now choose to attack enemy planets.
        val enemyOnPlayerSide = halfPlanets.filter{it.owner == player.opponent()}

        // Making a list of planets we will want to attack. These are filtered for all enemy planets and neutral planets on the enemy's side.
        // The filter applied is ensuring the planets in targeting aren't already being captured.
        val planetsToAttack = enemyOnPlayerSide.filter{val targetNet = planetInfo.getOrElse(it.id) { IncomingShipsInfo()}.netShips - it.nShips
            targetNet <= 0} + otherHalfPlanets.filter{val targetNet = planetInfo.getOrElse(it.id) {IncomingShipsInfo()}.netShips - it.nShips
            targetNet <= 0}

        // If there are no enemies left (final tick), do not return an action.
        if (planetsToAttack.isEmpty()) {
            return Action.doNothing()
        }

        // We want to find a small list of our optimal sources.
        // We use our list of possible source planets, sorted by descending order using the nShips values. Out of this list,
        // we take a dynamic amount, being the size of the full list * 0.3. (+1 is used to avoid a value of 0)
        val optimalSources: List<Planet> = mySourcePlanets.sortedByDescending {it.nShips}.take(max(1, ((mySourcePlanets.size * 0.3).toInt()+1)))

        // Now we collect our action. Returned via the appropriate method which takes our list of sources and targets.
        val action = attackPlanetWithSource(planetsToAttack, optimalSources, planetInfo)

        // Want to ensure we aren't attacking for no reason. If our attacking is using the default value of
        // source.nShips/2, and this value is a small amount (worked out dynamically), then we shouldn't waste our small
        // planet's ships.
        if (action.numShips == gameState.planets.get(action.sourcePlanetId).nShips /2) {
            if (gameState.planets.get(action.sourcePlanetId).nShips <= (source.growthRate * (params.maxTicks/12))) {
                return Action.doNothing()
            }
        }
        return action
    }


    // Method for attacking a planet with an input source.
    // We find the best planet to attack (determined by a heuristic), then return it in an action.
    fun attackPlanet(planetList: List<Planet>, source: Planet, planetInfo: MutableMap<Int, IncomingShipsInfo>): Action {
        // Default values to first planet in list in order to compare and store a minimum heuristic value.
        var target: Planet = planetList[0]
        var targetNet: Double = planetInfo.getOrElse(target.id) {IncomingShipsInfo()}.netShips - target.nShips
        var targetDist: Double = target.position.distance(source.position)
        // For all planets other than the first in the list.
        for (p in planetList.slice(1..planetList.lastIndex)) {
            // Calculate the net value of the planet (-nShips as it is not owned by the player).
            val incomingNet = planetInfo.getOrElse(p.id) {IncomingShipsInfo()}.netShips - p.nShips
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


    // Method for attacking a planet with a list of possible sources.
    // This will choose the best source, target pairing using unique heuristics for each. We then return them
    // in an action.
    fun attackPlanetWithSource(targetList: List<Planet>, sourceList: List<Planet>, planetInfo: MutableMap<Int, IncomingShipsInfo>): Action {
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
                val checkDist = t.position.distance(s.position)

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
        return Action(player, source.id, target.id, getShipsToSend(target.nShips+1, source))
    }

    // Method gets the necessary ships to send, will send the exact number of ships input
    // or the source's total ships / 2.
    fun getShipsToSend(needed: Double, source: Planet): Double {
        if (source.nShips >= needed) {
            return needed
        }
        return source.nShips / 2
    }

    // Calculates a heuristic score for targeting based on the input values.
    // If the target is an enemy planet, we focus on input netShips and distance,
    // if it is neutral, we also look at the growthRate to see the value of the neutral planet.
    fun getHeuristicValue(netShips: Double, distance: Double, target:Planet): Double {
        return if (target.owner == Player.Neutral) {
            (netShips + (distance * 0.5)) / target.growthRate+1
        } else {
            netShips + (distance*0.45)
        }
    }

    // Calculate the travel time for a given transporter to reach its target.
    // Calculated via the transporter's distance from its target / the transporter speed.
    fun getTravelTime(gameState: GameState, source: Int, target: Planet): Double {
        return (gameState.planets[source].transporter!!.s.distance(target.position)) / params.transporterSpeed
    }

    // Method to determine at which point in time, using the game's current state and knowable actions, the
    // planet in question will be captured. We then use this to return the total ships the planet being targeted will
    // produce.
    fun determineCaptureTime(gameState: GameState, source:Planet, planetInfo: MutableMap<Int, IncomingShipsInfo>): Double {
        // Can store the map for source as a value 'info'
        val info = planetInfo.getOrElse(source.id) {IncomingShipsInfo()}
        // If the net ship is already positive, no calculation or action needed at all.
        if (info.netShips + source.nShips >= 0) return 0.0
        if (info.attackingPlanetIds.size == 1) return getTravelTime(gameState, info.attackingPlanetIds[0], source)
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
        return "Defensive Reactive Agent 1.0"
    }
}

    fun main() {
    val agent = DefensiveReactiveAgent10()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}