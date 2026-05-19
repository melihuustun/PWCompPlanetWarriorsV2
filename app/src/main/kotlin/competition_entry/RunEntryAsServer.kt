package competition_entry

import games.planetwars.agents.GroupNAgents.evo.PlanetWarriorV2
import json_rmi.GameAgentServer

fun main() {
    val server = GameAgentServer(port = 8080, agentClass = PlanetWarriorV2::class)
    server.start(wait = true)
}
