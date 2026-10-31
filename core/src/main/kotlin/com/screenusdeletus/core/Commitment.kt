package com.screenusdeletus.core

/**
 * A commitment device: the user pledges an amount that they owe to a charity of their choice
 * each time they break a rule. Screenus Deletus never moves money itself — it keeps an honest ledger
 * and reminds the user to settle what they owe (optionally reported to their guardian).
 */
data class CommitmentDevice(
    val pledgedAmount: Double,
    val currency: String = "USD",
    /** Penalty for the first violation in a week. */
    val basePenalty: Double,
    /** Each further violation in the same week multiplies the penalty (1.5x, 2x…). */
    val escalationMultiplier: Double = 1.5,
    val charityName: String,
    val charityUrl: String,
    val violationsThisWeek: Int = 0,
    val totalViolations: Int = 0,
    val totalOwed: Double = 0.0,
    val totalSettled: Double = 0.0,
    val active: Boolean = true,
) {
    val outstanding: Double get() = (totalOwed - totalSettled).coerceAtLeast(0.0)
    val remainingPledge: Double get() = (pledgedAmount - totalOwed).coerceAtLeast(0.0)
}

object CommitmentLedger {
    fun nextPenalty(c: CommitmentDevice): Double {
        var p = c.basePenalty
        repeat(c.violationsThisWeek) { p *= c.escalationMultiplier }
        return roundCents(p.coerceAtMost(c.remainingPledge))
    }

    fun recordViolation(c: CommitmentDevice): Pair<CommitmentDevice, Double> {
        if (!c.active || c.remainingPledge <= 0.0) return c to 0.0
        val penalty = nextPenalty(c)
        return c.copy(
            violationsThisWeek = c.violationsThisWeek + 1,
            totalViolations = c.totalViolations + 1,
            totalOwed = roundCents(c.totalOwed + penalty),
        ) to penalty
    }

    fun settle(c: CommitmentDevice, amount: Double): CommitmentDevice =
        c.copy(totalSettled = roundCents((c.totalSettled + amount).coerceAtMost(c.totalOwed)))

    fun newWeek(c: CommitmentDevice): CommitmentDevice = c.copy(violationsThisWeek = 0)

    private fun roundCents(v: Double) = Math.round(v * 100.0) / 100.0
}
