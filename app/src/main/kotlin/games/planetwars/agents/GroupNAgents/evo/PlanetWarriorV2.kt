package games.planetwars.agents.GroupNAgents.evo

//import games.planetwars.agents.evo.GameStateWrapper

import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.*
import games.planetwars.agents.GroupNAgents.DefensiveReactiveAgent.DefensiveReactiveAgent10
import games.planetwars.agents.GroupNAgents.DefensiveReactiveAgent.DefensiveReactiveAgent11
import games.planetwars.agents.evo.SimpleEvoAgent
import games.planetwars.agents.evo.SimpleEvoAgent.ScoredSolution
import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.core.*
import kotlin.FloatArray
import kotlin.Int
import kotlin.random.Random

data class InitialModelDepthV2(val p: FloatArray, val length: Int)

data class FinalGameStateWrapperV2(
    val gameState: GameState,
    val params: GameParams,
    val player: Player,
    val opponentModel: PlanetWarsAgent = DoNothingAgent(),
    val initialModel: PlanetWarsAgent = DoNothingAgent(),
    var enemyGrowthRateWeight: Double = 1.0,
) {
    var forwardModel = AdvancedForwardModel(gameState, params)

    companion object {
        // nShips is also included in forward model being used
        val shiftBy = 3
        //private const val MIN_ACTION_SHIPS_EPS = 1e-9
        private const val MIN_ACTION_SHIPS_EPS = 0.0
    }

    fun getAction(gameState: GameState, from: Float, to: Float, numShips: Float = 0.5f): Action {
        // filter the planets that are owned by the player AND have a transporter available
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        // filter the planets that are owned by the player AND have a transporter available
        if (myPlanets.isEmpty()) {
            return Action.doNothing()
        }
        // now find a random target planet
        val otherPlanets = gameState.planets.filter { it.owner == player.opponent() || it.owner == Player.Neutral }
        if (otherPlanets.isEmpty()) {
            return Action.doNothing()
        }
        val source = myPlanets[(from * myPlanets.size).toInt()]
        val target = otherPlanets[(to * otherPlanets.size).toInt()]
        val shipsToSend = source.nShips * numShips
        // Treat non-positive launches as explicit no-ops so they do not consume the source transporter slot.
        if (shipsToSend <= MIN_ACTION_SHIPS_EPS) {
            return Action.doNothing()
        }
        return Action(player, source.id, target.id, shipsToSend)
    }

    fun runForwardModel(seq: FloatArray): Double {
        var ix = 0;
        forwardModel = AdvancedForwardModel(gameState.deepCopy(), params)
        while (ix < seq.size && !forwardModel.isTerminal()) {
            val from = seq[ix]
            val to = seq[ix + 1]
            val nShips = seq[ix + 2]

            var myAction = Action.doNothing()
            if (from == -1f || to == -1f || nShips == -1f) {
                myAction = Action.doNothing()
            }
            else {
                // The gameState is taken from the forward model to allow for dynamic reactive gameplay
                myAction = getAction(forwardModel.state, from, to, nShips)
            }
            val opponentAction = opponentModel.getAction(forwardModel.state)
            val actions = mapOf(player to myAction, player.opponent() to opponentAction)
            forwardModel.step(actions)
            ix += shiftBy
        }
        return scoreDifference()
    }

    fun initialForwardModel(seqLength: Int): InitialModelDepthV2 {
        forwardModel = AdvancedForwardModel(gameState.deepCopy(), params)

        val p = FloatArray(seqLength)
        var ix = 0

        while (!forwardModel.isTerminal() && ix < seqLength) {
            val playerAction = initialModel.getAction(forwardModel.state.deepCopy())
            val opponentAction = opponentModel.getAction(forwardModel.state.deepCopy())

            val myPlanets = forwardModel.state.planets.filter { it.owner == player && it.transporter == null }
            val otherPlanets = forwardModel.state.planets.filter { it.owner == player.opponent() || it.owner == Player.Neutral }


            var from = 0
            var to = 0
            // inverse of the following:
            //val source = myPlanets[(from * myPlanets.size).toInt()]
            //val target = otherPlanets[(to * otherPlanets.size).toInt()]
            for (i in 0 until myPlanets.size) {
                if (playerAction.sourcePlanetId == myPlanets[i].id) {
                    from = i
                }
            }
            for (i in 0 until otherPlanets.size) {
                if (playerAction.destinationPlanetId == otherPlanets[i].id) {
                    to = i
                }
            }

            val sourcePlanet = forwardModel.state.planets.find { it.id == playerAction.sourcePlanetId }
            var nShipsFraction = if (sourcePlanet != null && sourcePlanet.nShips > 0.0 && playerAction != Action.doNothing()) {
                (playerAction.numShips / sourcePlanet.nShips).toFloat().coerceIn(0.01f, 1.0f)
            } else {
                -1f
            }

            //if (nShipsFraction < 0.05) {
            //    nShipsFraction = 0.5f
            //}

            p[ix] = if (myPlanets.isNotEmpty()) from.toFloat() / myPlanets.size else -1f
            p[ix + 1] = if (otherPlanets.isNotEmpty()) to.toFloat() / otherPlanets.size else -1f
            p[ix + 2] = nShipsFraction
            ix += shiftBy

            val actions = mapOf(
                player to playerAction,
                player.opponent() to opponentAction,
            )
            forwardModel.step(actions)
        }

        return InitialModelDepthV2(p, ix)
    }

    fun scoreDifference(): Double {
        // allow standalone use of this as well
        return forwardModel.getShips(player) - forwardModel.getShips(player.opponent())
    }

    fun growthRateDifference(): Double {
        return forwardModel.getGrowthRate(player) - enemyGrowthRateWeight * forwardModel.getGrowthRate(player.opponent())
    }

    fun growthRate(): Double {
        return forwardModel.getGrowthRate(player)
    }
}

data class PlanetWarriorV2(
    var flipAtLeastOneValue: Boolean = true,
    var probMutation: Double = 0.45,
    //var sequenceLength: Int = 200,
    var sequenceLength: Int = 450,
    var nEvals: Int = 20,
    var useShiftBuffer: Boolean = true,
    var epsilon: Double = 1e-6,
    var timeLimitMillis: Long = 48,
    var opponentModel: PlanetWarsAgent = DoNothingAgent(),
    var initialModel: PlanetWarsAgent = DefensiveReactiveAgent11(),
    var secondModel: PlanetWarsAgent = CarefulRandomAgent(),
    var economyWeight: Double = 75.0,
    var economyWeight2: Double = 0.2,
    var secondPart: Double = 0.35,

    ) : PlanetWarsPlayer() {
    override fun getAgentType(): String {
        return "PlanetWarriorV2-$sequenceLength-$nEvals-$probMutation-$useShiftBuffer"
    }

    var initialReModeled = false

    // neutral bug fixed by preparing the enemy agent
    override fun prepareToPlayAs(player: Player, params: GameParams, opponent: String?): String {
        super.prepareToPlayAs(player, params, opponent)
        opponentModel.prepareToPlayAs(player.opponent(), params, opponent)
        initialModel.prepareToPlayAs(player, params, opponent)
        secondModel.prepareToPlayAs(player, params, opponent)
        return getAgentType()
    }

    internal var random = Random

    // these are all the parameters that control the agend
//    internal var buffer: FloatArray? = null // randomPoint(sequenceLength)


    var bestSolution: ScoredSolution? = null

    data class ScoredSolution(val score: Double, val solution: FloatArray)

    override fun getAction(gameState: GameState): Action {

        // start timer
        val startTime = System.currentTimeMillis()
        var ecoWeight: Double = economyWeight

        if (gameState.gameTick >= params.maxTicks - 200) {
            ecoWeight = 0.0
        }
        else if (gameState.gameTick + sequenceLength / 3 > params.maxTicks * secondPart) {
            ecoWeight = economyWeight * economyWeight2
        }

        if (bestSolution == null || !useShiftBuffer) {
            val solution = initialPoint(gameState, initialModel)
            val scores = evalSeq(gameState, solution)
            bestSolution = ScoredSolution(scores.score + ecoWeight * scores.growthRate, solution)
        } else {
            val nextSeq = shiftLeftAndRandomAppend(bestSolution!!.solution, FinalGameStateWrapperV2.shiftBy)
            val scores = evalSeq(gameState, nextSeq)
            bestSolution = ScoredSolution(scores.score + ecoWeight * scores.growthRate, nextSeq)

            if (gameState.gameTick % 80 == 0) {
                val mut1 = initialPoint(gameState, initialModel)
                val scores1 = evalSeq(gameState, mut1)
                val mutScore1 = scores1.score + ecoWeight * scores1.growthRate
                if (mutScore1 >= bestSolution!!.score) {
                    bestSolution = ScoredSolution(mutScore1, mut1)
                }
            }
            //else if (gameState.gameTick % 40 == 20) {
            //    val mut1 = initialPoint(gameState, secondModel)
            //    val scores1 = evalSeq(gameState, mut1)
            //    val mutScore1 = scores1.score + ecoWeight * scores1.growthRate
            //    if (mutScore1 >= bestSolution!!.score) {
            //        bestSolution = ScoredSolution(mutScore1, mut1)
            //    }
            //}

            if (gameState.gameTick >= params.maxTicks - 10 * params.transporterSpeed * 2) {
                val mut0 = zeroPoint()
                val scores0 = evalSeq(gameState, mut0)
                val mutScore0 = scores0.score + ecoWeight * scores0.growthRate
                if (mutScore0 >= bestSolution!!.score) {
                    bestSolution = ScoredSolution(mutScore0, mut0)
                }
            }

        }

        // Time-bounded evolution: mutate and evaluate until the time budget runs out
        while (gameState.gameTick > gameState.planets.filter { it.owner == player }.size * 0 && System.currentTimeMillis() - startTime < timeLimitMillis) {
            //println(System.currentTimeMillis() - startTime)
            val mut = mutate(bestSolution!!.solution, probMutation)
            val scores = evalSeq(gameState, mut)
            val mutScore = scores.score + ecoWeight * scores.growthRate
            if (mutScore >= bestSolution!!.score) {
                bestSolution = ScoredSolution(mutScore, mut)
            }
        }

        if (bestSolution!!.solution[0] == -1f || bestSolution!!.solution[1] == -1f || bestSolution!!.solution[2] == -1f) {
            return Action.doNothing()
        }

        val wrapper = FinalGameStateWrapperV2(gameState, params, player)
        //val numShipsFraction = bestSolution!!.solution[2].coerceAtLeast(0.3f)
        //val action = wrapper.getAction(gameState, bestSolution!!.solution[0], bestSolution!!.solution[1], numShipsFraction)
        //if (bestSolution!!.solution[2] < 0.1) {
        //    return Action.doNothing()
        //}
        val action = wrapper.getAction(gameState, bestSolution!!.solution[0], bestSolution!!.solution[1], bestSolution!!.solution[2])
        return action
    }

    private fun mutate(v: FloatArray, mutProb: Double): FloatArray {

        val n = v.size
        val x = FloatArray(n)
        // pointwise probability of additional mutations
        // choose element of vector to mutate
        var ix = random.nextInt(n)
        if (!flipAtLeastOneValue) {
            // setting this to -1 means it will never match the first clause in the if statement in the loop
            // leaving it at the randomly chosen value ensures that at least one bit (or more generally value) is always flipped
            ix = -1
        }
        // copy all the values faithfully apart from the chosen one
        for (i in 0 until n) {
            //if (i == ix || random.nextDouble() < mutProb) {
            if (i == ix || random.nextDouble() < mutProb * ((n.toDouble() -  i.toDouble()) / n.toDouble())) {
                x[i] = random.nextFloat()
            } else {
                x[i] = v[i]
            }
        }
        return x
    }

    // random point in n-dimensional space in unit hypercube; n = sequenceLength
    //private fun randomPoint(): FloatArray {
    //    val p = FloatArray(sequenceLength)
    //    for (i in p.indices) {
    //        p[i] = random.nextFloat()
    //    }
    //    return p
    //}

    private fun initialPoint(gameState: GameState, model: PlanetWarsAgent): FloatArray {
        //val p = FloatArray(sequenceLength)
        //var forwardModel = AdvancedForwardModel(gameState.deepCopy(), params)
        val wrapper = FinalGameStateWrapperV2(gameState, params, player, initialModel = model)
        val init = wrapper.initialForwardModel(sequenceLength)
        val p = init.p
        val length = init.length
        //println(length)

        for (i in length until p.size) {
            p[i] = random.nextFloat()
        }
        return p
    }

    private fun zeroPoint(): FloatArray {
        val p = FloatArray(sequenceLength)
            for (i in p.indices) {
                p[i] = -1f
            }
        return p
    }

    private fun shiftLeftAndRandomAppend(v: FloatArray, shiftBy: Int): FloatArray {
        val p = FloatArray(v.size)
        for (i in 0 until p.size - shiftBy) {
            p[i] = v[i + shiftBy]
        }
        // TODO: this is a bit of a hack, but it should work when shiftBy is 2, which it is for now
        // shift by n
        for (i in 1 until shiftBy + 1) {
            p[p.size - i] = random.nextFloat()
        }
        //p[p.size - 1] = random.nextFloat()
        //p[p.size - 2] = random.nextFloat()
        return p
    }

    data class EvalResults(val score: Double, val growthRate: Double)

    private fun evalSeq(state: GameState, seq: FloatArray): EvalResults {
        val wrapper = FinalGameStateWrapperV2(state.deepCopy(), params, player, opponentModel)
        wrapper.runForwardModel(seq)
        val score = wrapper.scoreDifference()
        val growthRate = wrapper.growthRateDifference()
        //val growthRate = wrapper.growthRate()
        return EvalResults(score, growthRate)
    }
}

fun main() {
    val gameParams = GameParams(numPlanets = 10)
    val gameState = GameStateFactory(gameParams).createGame()
    val agent = AdvancedEvoAgent()
    agent.prepareToPlayAs(Player.Player1, gameParams)
    println(agent.getAgentType())
    val action = agent.getAction(gameState)
    println(action)
}