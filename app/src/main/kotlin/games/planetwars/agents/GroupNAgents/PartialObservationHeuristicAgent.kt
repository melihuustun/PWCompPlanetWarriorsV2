package games.planetwars.agents.GroupNAgents

import util.Vec2d
import games.planetwars.agents.Action
import games.planetwars.agents.PartialObservationPlayer
import games.planetwars.core.*

class PartialObservationDefensiveHeuristicAgent(): PartialObservationPlayer() {

    // Define the halfway line of the screen.
    private val halfwayLine = params.width/2

    private val centerPoint = Vec2d(params.width/2.0,params.height/2.0)
    override fun getAction(observation: Observation): Action { //Passes Observation of current game state.

        // Define a condition based on a condition. if we are player1, store the condition method onPlayerHalf
        // as position.x <= halfwayLine, otherwise, as position.x >= halfwayLine.
        val onPlayerHalf: (PlanetObservation) -> Boolean = if (player == Player.Player1) {
            {it.position.x <= halfwayLine}
        } else {
            {it.position.x >= halfwayLine}
        }

        val totalObservedPlanets: Int = observation.observedPlanets.size
        val halfPlanets = ArrayList<PlanetObservation>(totalObservedPlanets)
        var target: PlanetObservation
        val myPlanets = ArrayList<PlanetObservation>(totalObservedPlanets)
        val opposingPlanets = ArrayList<PlanetObservation>(totalObservedPlanets)

        for (p in observation.observedPlanets) {
            if (onPlayerHalf(p) && p.owner != player) {
                halfPlanets.add(p)
            }
            if (p.owner == player) {
                if (p.transporter == null) {
                    myPlanets.add(p)
                }
            } else { opposingPlanets.add(p)}
        }

        // If there are no planets to use, do nothing.
        if (myPlanets.isEmpty()) {
            return Action.doNothing()
        }

        // If there is nothing to defend / capture on our side
        if (halfPlanets.isEmpty()) {
            // If nothing to attack (game is over) return nothing.
            if (opposingPlanets.isEmpty()) {
                return Action.doNothing()
            }
            // Target the closest planet in the list.
            target = opposingPlanets.minBy{getHeuristicScore(it,onPlayerHalf)}
        }
        else {
            // Target the closest uncaptured planet on our side.
            target = halfPlanets.minBy{getHeuristicScore(it, onPlayerHalf)}
        }

        val source = myPlanets.minBy{getSourceHeuristicScore(it, target)}
        // Collect the observed ships owned by the selected source.
        val sourceShips = observation.observedPlanets[source.id].nShips

        // Return the appropriate action.
        return Action(player, source.id, target.id, sourceShips!!/2)
    }

    fun getHeuristicScore(p: PlanetObservation, onPlayerHalf: (PlanetObservation) -> Boolean ): Double {
        // Prioritise capture of higher growth rate planets on our side
        if (onPlayerHalf(p)) {
            return p.position.distance(centerPoint) / 2
        }
        // Capture the nearest planets to center where planets with a significantly higher growthRate are prioritised.
        return p.position.distance(centerPoint)
    }

    fun getSourceHeuristicScore(s: PlanetObservation, t: PlanetObservation): Double {
        // Want short distance, high nShips source.
        return s.position.distance(t.position) / s.nShips!!
    }

    override fun getAgentType(): String {
        return "Defensive Heuristic Agent"
    }
}