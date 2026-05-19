package games.planetwars.agents.GroupNAgents.MCTS

import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.random.Random

data class TreeNode(
    val state: GameState,
    val parent: TreeNode?,
    val action: Action?,
    var children: MutableMap<Action, TreeNode>,
    var availableActions: MutableList<Action>,
    var visits: Int,
    var totValue: Double,
    val depth: Int = if (parent == null) 0 else parent.depth + 1,
)