package games.planetwars.agents.GroupNAgents

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.GameParams
import games.planetwars.core.GameState
import games.planetwars.core.GameStateFactory
import games.planetwars.core.Player

class NearbyNeutralNearbyFallbackAgent() : PlanetWarsPlayer() {
    override fun getAction(gameState: GameState): Action {
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        if (myPlanets.isEmpty()) {
            return Action.doNothing()
        }
        
        val source = myPlanets.random()
        
        // Find closest neutral planet
        val neutralPlanets = gameState.planets.filter { it.owner == Player.Neutral }
        if (neutralPlanets.isNotEmpty()) {
            // First target closest neutral
            val target = neutralPlanets.minByOrNull { it.position.distance(source.position) }!!
            return Action(playerId = player, sourcePlanetId = source.id, destinationPlanetId = target.id, numShips = source.nShips/2)
        }
        
        // PHASE 3: Fall back to randomly targeting the opponent if no neutrals are left
        val opponentPlanets = gameState.planets.filter { it.owner == player.opponent() }
        if (opponentPlanets.isNotEmpty()) {
            val target = opponentPlanets.minByOrNull { it.position.distance(source.position) }!!
            return Action(playerId = player, sourcePlanetId = source.id, destinationPlanetId = target.id, numShips = source.nShips/2)
        }
        
        return Action.doNothing()
    }

    override fun getAgentType(): String {
        return "Nearby Neutral Nearby Fallback Agent"
    }
}


fun main() {
    val agent = NearbyNeutralNearbyFallbackAgent()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}