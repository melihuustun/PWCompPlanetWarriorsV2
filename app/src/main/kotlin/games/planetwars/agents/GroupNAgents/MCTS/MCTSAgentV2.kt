package games.planetwars.agents.GroupNAgents.MCTS

import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.GroupNAgents.DefensiveReactiveAgent.DefensiveReactiveAgent11
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.core.*
import java.lang.Double.NEGATIVE_INFINITY
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class MCTSAgentV2() : PlanetWarsPlayer() {

    // var bestMove = Action.doNothing()
    var opponentModel = DoNothingAgent()
    val rolloutLength = 200
    val epsilon = 1e-6
    val k = sqrt(2.0)
    val maxTreeDepth = 1000
    val timeLimitMillis = 48
    // For testing/debugging purposes - track number of iterations per tick/move
    // var numIters = 0

    override fun getAction(gameState: GameState): Action {
        //numIters = 0
        val state = gameState.deepCopy()
        val root = TreeNode(state, null, null, mutableMapOf(), generateAvailableActions(state),1,0.0)
        mctsSearch(root)
        val bestMove = root.children.maxByOrNull { it.value.visits }?.key
        //println("Iterations - $numIters")
        return bestMove ?: Action.doNothing()
    }


    fun generateAvailableActions(gameState: GameState): MutableList<Action> {
        val actions: MutableList<Action> = mutableListOf(Action.doNothing())

        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        if (myPlanets.isEmpty()) {
            return actions
        }

        val targetPlanets = gameState.planets.filter { it.owner == player.opponent() || it.owner == Player.Neutral }
        if (targetPlanets.isEmpty()) {
            return actions
        }

        val shipValues = listOf(0.5, 0.75)

        for (source in myPlanets) {
            for (target in targetPlanets) {
                for (fract in shipValues) {
                    val shipsToSend = source.nShips*fract
                    if (shipsToSend < 3) {
                        continue
                    }
                    if (target.owner == Player.Neutral && shipsToSend < target.nShips) {
                        continue
                    }
                    actions.add(Action(player, source.id, target.id, shipsToSend))
                }
            }
        }
        return actions
    }

    fun expand(node: TreeNode) : TreeNode {
        val notChosen = node.availableActions
        val chosen = notChosen[(Random.nextInt(notChosen.size))]
        notChosen.remove(chosen)
        val opponentAction = opponentModel.getAction(node.state.deepCopy())

        val newState = node.state.deepCopy()
        val forwardModel = MCTSForwardModel(newState, params)
        val actions = mapOf(player to chosen, player.opponent() to opponentAction)
        forwardModel.step(actions)


        val newNode = TreeNode(newState, node, chosen, mutableMapOf(), generateAvailableActions(newState), 0, 0.0)
        node.children.put(chosen, newNode)

        return newNode


    }

    fun mctsSearch(node: TreeNode) {
        val time = System.currentTimeMillis()
        var stop = false

        while (!stop) {
            val selected = treePolicy(node)
            val delta = rollout(selected)
            backup(selected, delta)
            //numIters++

            val elapsed = System.currentTimeMillis() - time
            stop = elapsed >= timeLimitMillis
        }

    }

    fun treePolicy(node: TreeNode) : TreeNode {
        var current = node

        while (current.depth < maxTreeDepth) {
            val fm = MCTSForwardModel(current.state, params)

            if (fm.isTerminal()) {
                break
            }

            if (current.availableActions.isNotEmpty()) {
                // expand returns the new child
                return expand(current)
            } else if (current.children.isNotEmpty()) {
                val actionChosen = ucb(current)
                current = current.children[actionChosen]?: break
            } else {
                break
            }
        }
        return current
    }

    fun ucb(node: TreeNode) : Action {
        var bestAction: Action? = null
        var bestValue = NEGATIVE_INFINITY

        for (action in node.children.keys) {
            val child = node.children[action]
            if (child == null) {
                throw AssertionError("Should not be here")
            } else if (bestAction == null) {
                bestAction = action
            }
            val hvVal = child.totValue
            val childValue = hvVal / (child.visits + epsilon)

            val explorationTerm = k * sqrt(ln(node.visits + 1.0) / (child.visits + epsilon))

            val uctValue = childValue + explorationTerm + epsilon * Random.nextDouble()

            if (uctValue > bestValue) {
                bestAction = action
                bestValue = uctValue
            }
        }
        return bestAction ?: Action.doNothing()

    }
    fun rollout(node: TreeNode) : Double {
        var rolloutDepth = 0
        val rolloutState = node.state.deepCopy()
        val forwardModel = MCTSForwardModel(rolloutState, params)

        if (rolloutLength > 0) {
            while (!finishRollout(forwardModel, rolloutDepth)) {
                val actions = generateAvailableActions(forwardModel.state)
                val chosen: Action =
                    if (actions.isEmpty()) {
                        Action.doNothing()
                    }
                    else {
                        actions[Random.nextInt(actions.size)]
                    }
                val opponentAction = opponentModel.getAction(forwardModel.state)

                forwardModel.step(mapOf(player to chosen, player.opponent() to opponentAction))
                rolloutDepth++
            }
        }
        return (forwardModel.getShips(player) - forwardModel.getShips(player.opponent())) + (20 * (forwardModel.getGrowthRate(player) - forwardModel.getGrowthRate(player.opponent()))) + (forwardModel.getPlanets(player) - forwardModel.getPlanets(player.opponent()))
    }

    fun finishRollout(fm: MCTSForwardModel, depth: Int) : Boolean{
        if (depth >= rolloutLength) {
            return true
        }
        return fm.isTerminal()

    }

    fun backup(startNode: TreeNode, result: Double) {
        var current : TreeNode? = startNode
        while (current != null) {
            current.visits++
            current.totValue += result
            current = current.parent
        }
    }

    override fun getAgentType(): String {
        return "MCTS Agent V2"
    }

    override fun prepareToPlayAs(player: Player, params: GameParams, opponent: String?): String {
        super.prepareToPlayAs(player, params, opponent)
        opponentModel.prepareToPlayAs(player.opponent(), params)
        return getAgentType()
    }
}

fun main() {
    val gameParams = GameParams(numPlanets = 10)
    val gameState = GameStateFactory(gameParams).createGame()
    val agent = MCTSAgentV2()
    agent.prepareToPlayAs(Player.Player1, gameParams)
    println(agent.getAgentType())
    val action = agent.getAction(gameState)
    println(action)
}
