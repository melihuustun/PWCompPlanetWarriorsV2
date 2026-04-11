package games.planetwars.agents.GroupNAgents.evo

//import games.planetwars.agents.evo.GameStateWrapper

import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.*
import games.planetwars.agents.GroupNAgents.DefensiveReactiveAgent.DefensiveReactiveAgent10
import games.planetwars.agents.evo.SimpleEvoAgent
import games.planetwars.agents.evo.SimpleEvoAgent.ScoredSolution
import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.core.*
import kotlin.random.Random

data class FinalGameStateWrapper(
    val gameState: GameState,
    val params: GameParams,
    val player: Player,
    val opponentModel: PlanetWarsAgent = DoNothingAgent(),
) {
    var forwardModel = AdvancedForwardModel(gameState, params)

    companion object {
        // nShips is also included in forward model being used
        val shiftBy = 3
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
        return Action(player, source.id, target.id, source.nShips * numShips)
    }

    fun runForwardModel(seq: FloatArray): Double {
        var ix = 0;
        forwardModel = AdvancedForwardModel(gameState.deepCopy(), params)
        while (ix < seq.size && !forwardModel.isTerminal()) {
            val from = seq[ix]
            val to = seq[ix + 1]
            val nShips = seq[ix + 2]
            // The gameState is taken from the forward model to allow for dynamic reactive gameplay
            val myAction = getAction(forwardModel.state, from, to, nShips)
            val opponentAction = opponentModel.getAction(forwardModel.state)
            val actions = mapOf(player to myAction, player.opponent() to opponentAction)
            forwardModel.step(actions)
            ix += shiftBy
        }
        return scoreDifference()
    }

    fun scoreDifference(): Double {
        // allow standalone use of this as well
        return forwardModel.getShips(player) - forwardModel.getShips(player.opponent())
    }

    fun growthRateDifference(): Double {
        return forwardModel.getGrowthRate(player) - forwardModel.getGrowthRate(player.opponent())
    }
}

data class FinalAgent(
    var flipAtLeastOneValue: Boolean = true,
    var probMutation: Double = 0.5,
    //var sequenceLength: Int = 200,
    var sequenceLength: Int = 600,
    var nEvals: Int = 20,
    var useShiftBuffer: Boolean = true,
    var epsilon: Double = 1e-6,
    var timeLimitMillis: Long = 20,
    var opponentModel: PlanetWarsAgent = DoNothingAgent(),
    var economyWeight: Double = 75.0,
    var ecoWeight: Double = economyWeight,
    var secondPart: Double = 0.35,

    ) : PlanetWarsPlayer() {
    override fun getAgentType(): String {
        return "FinalAgent-$sequenceLength-$nEvals-$probMutation-$useShiftBuffer"
    }

    // neutral bug fixed by preparing the enemy agent
    override fun prepareToPlayAs(player: Player, params: GameParams, opponent: String?): String {
        super.prepareToPlayAs(player, params, opponent)
        opponentModel.prepareToPlayAs(player.opponent(), params, opponent)
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

        var inSecondPart = false

        if (inSecondPart || gameState.gameTick + sequenceLength * 0.5 > params.maxTicks * secondPart) {
            ecoWeight = economyWeight * 0.3
            inSecondPart = true
        }

        if (bestSolution == null || !useShiftBuffer) {
            val solution = initialPoint()
            val scores = evalSeq(gameState, solution)
            bestSolution = ScoredSolution(scores.score + ecoWeight * scores.growthRate, solution)
        } else {
            val nextSeq = shiftLeftAndRandomAppend(bestSolution!!.solution, FinalGameStateWrapper.shiftBy)
            val scores = evalSeq(gameState, nextSeq)
            bestSolution = ScoredSolution(scores.score + ecoWeight * scores.growthRate, nextSeq)
        }

        // Time-bounded evolution: mutate and evaluate until the time budget runs out
        while (System.currentTimeMillis() - startTime < timeLimitMillis) {
            //println(System.currentTimeMillis() - startTime)
            val mut = mutate(bestSolution!!.solution, probMutation)
            val scores = evalSeq(gameState, mut)
            val mutScore = scores.score + ecoWeight * scores.growthRate
            if (mutScore >= bestSolution!!.score) {
                bestSolution = ScoredSolution(mutScore, mut)
            }
        }

        val wrapper = FinalGameStateWrapper(gameState, params, player)
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
            if (i == ix || random.nextDouble() < mutProb) {
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

    private fun initialPoint(): FloatArray {
        val p = FloatArray(sequenceLength)
        //var forwardModel = AdvancedForwardModel(gameState.deepCopy(), params)
        for (i in p.indices) {
            p[i] = random.nextFloat()
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
        val wrapper = FinalGameStateWrapper(state.deepCopy(), params, player, opponentModel)
        wrapper.runForwardModel(seq)
        val score = wrapper.scoreDifference()
        val growthRate = wrapper.growthRateDifference()
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