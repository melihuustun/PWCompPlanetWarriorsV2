package games.planetwars.agents.GroupNAgents

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.GameParams
import games.planetwars.core.GameState
import games.planetwars.core.GameStateFactory
import games.planetwars.core.Player

class NearbyNeutralAgent() : PlanetWarsPlayer() {
    override fun getAction(gameState: GameState): Action {
        // filter the planets that are owned by the player AND have a transporter available
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        if (myPlanets.isEmpty()) {
            return Action.doNothing()
        }
        // now find a random target planet not owned by the player or the opponent
        val neutralPlanets = gameState.planets.filter { it.owner == Player.Neutral }
        if (neutralPlanets.isEmpty()) {
            return Action.doNothing()
        }
        val source = myPlanets.random()
        // PHASE 2: Target the closest neutral planet using minByOrNull
        val target = neutralPlanets.minByOrNull { it.position.distance(source.position) }!!
        return Action(playerId = player, sourcePlanetId = source.id, destinationPlanetId = target.id, numShips = source.nShips/2)
    }

    override fun getAgentType(): String {
        return "Nearby Neutral Agent"
    }
}


fun main() {
    val agent = NearbyNeutralAgent()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}