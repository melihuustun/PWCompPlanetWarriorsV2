package games.planetwars.agents.GroupNAgents

import games.planetwars.agents.Action
import games.planetwars.agents.PartialObservationPlayer
import games.planetwars.core.*

class PartialObservationDefensiveRandomAgent(): PartialObservationPlayer() {
    override fun getAction(observation: Observation): Action { //Passes Observation of current game state.

        // Collect all planets eligible to be sources. We check for planets that are owned by the player, have no
        // active transporters and have ships to use.
        val myPlanets = observation.observedPlanets.filter { it.owner == player && it.transporter == null && it.nShips!! > 0}

        // If there are no planets to use, do nothing.
        if (myPlanets.isEmpty()) {
            return Action.doNothing()
        }

        // Choose a random source.
        val source = myPlanets.random()
        // Define the halfway line of the screen.
        val halfwayLine = params.width/2

        var halfPlanets: List<PlanetObservation>
        var target: PlanetObservation

        // Define which planets to look at first based on where we are (this is the Defensive aspect).
        if (player == Player.Player1) { //If Player1, choose from planets on left side.
            halfPlanets = observation.observedPlanets.filter {it.position.x <= halfwayLine && (it.owner == player.opponent() || it.owner == Player.Neutral) && it != source}
            //Filter for planets on left half of screen where the agent owns it or is neutral
        }
        else { //If Player2, choose from planets on right side
            halfPlanets = observation.observedPlanets.filter {it.position.x >= halfwayLine && (it.owner == player.opponent() || it.owner == Player.Neutral) && it != source }
            //Filter for planets on right half of screen where the agent owns it or is neutral
        }

        // If there is nothing to defend / capture on our side
        if (halfPlanets.isEmpty()) {
            // Choose an enemy/neutral planet on the other side of the map to attack
            val opposingPlanets = observation.observedPlanets.filter{it.owner == player.opponent() || it.owner == Player.Neutral}
            // If nothing to attack (game is over) return nothing.
            if (opposingPlanets.isEmpty()) {
                return Action.doNothing()
            }
            // Target the closest planet in the list.
            target = opposingPlanets.minBy{it.position.distance(source.position)}
        }
        else {
            // Target the closest uncaptured planet on our side.
            target = halfPlanets.minBy{it.position.distance(source.position)}
        }

        // Collect the observed ships owned by the selected source.
        val sourceShips = observation.observedPlanets[source.id].nShips

        // Return the appropriate action.
        return Action(player, source.id, target.id, sourceShips!!/2)
    }

    override fun getAgentType(): String {
        return "Defensive Random Agent"
    }
}