package games.planetwars.agents.GroupNAgents.MCTS

import competition_entry.GreedyHeuristicAgent
import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.core.*
import jdk.jfr.internal.Cutoff.INFINITY
import java.lang.Double.NEGATIVE_INFINITY
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class MCTSAgent() : PlanetWarsPlayer() {

    // var bestMove = Action.doNothing()
    var opponentModel = DoNothingAgent()
    val rolloutLength = 200
    val epsilon = 1e-6
    val k = sqrt(2.0)
    val maxTreeDepth = 500
    val timeLimitMillis = 30
    var numIters = 0

    // Future refinements:
    // 1. Prune the search space of possible actions to consider more logical/informed actions rather than every possible action
    // 2. Implement time limit instead of max iterations limit
    // e.g. val timeBudget = 10 (ms)
    // 3. Use random seed for better consistency
    // e.g. val rnd = Random(123456)

    override fun getAction(gameState: GameState): Action {
        numIters = 0
        val state = gameState.deepCopy()
        val root = TreeNode(state, null, null, mutableMapOf(), generateAvailableActions(state),1,0.0)
        mctsSearch(root)
        val bestMove = root.children.maxByOrNull { it.value.visits }?.key
        println("Iterations - $numIters")
        return bestMove ?: Action.doNothing()
    }

    fun generateAvailableActions(gameState: GameState): MutableList<Action> {
        val actions: MutableList<Action> = mutableListOf()

        var myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        if (myPlanets.isEmpty()) {
            return actions
        }

        var targetPlanets = gameState.planets.filter { it.owner == player.opponent() || it.owner == Player.Neutral }
        if (targetPlanets.isEmpty()) {
            return actions
        }

        val shipValues = mutableListOf(0.5, 0.75)

        for (source in myPlanets) {
            for (target in targetPlanets) {
                for (fract in shipValues) {
                    actions.add(Action(player, source.id, target.id, source.nShips * fract))
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
            numIters++

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

            val uctValue = childValue + explorationTerm + epsilon*Random.nextDouble()

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
                val actions = generateAvailableActions(rolloutState)
                val chosen: Action =
                if (actions.isEmpty()) {
                    Action.doNothing()
                }
                else {
                    actions[Random.nextInt(actions.size)]
                }
                val opponentAction = opponentModel.getAction(rolloutState)

                forwardModel.step(mapOf(player to chosen, player.opponent() to opponentAction))
                rolloutDepth++
            }
        }
        return forwardModel.getShips(player) - forwardModel.getShips(player.opponent()) + 5 * (forwardModel.getGrowthRate(player) - forwardModel.getGrowthRate(player.opponent())) + (forwardModel.getPlanets(player) - forwardModel.getPlanets(player.opponent()))
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
        return "MCTS Agent"
    }
}

fun main() {
    val gameParams = GameParams(numPlanets = 10)
    val gameState = GameStateFactory(gameParams).createGame()
    val agent = MCTSAgent()
    agent.prepareToPlayAs(Player.Player1, gameParams)
    println(agent.getAgentType())
    val action = agent.getAction(gameState)
    println(action)
}
