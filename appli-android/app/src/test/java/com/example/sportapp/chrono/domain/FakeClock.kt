package com.example.sportapp.feature.chrono.domain

/**
 * Clock controllable manuellement pour les tests JVM des state machines chrono.
 * À utiliser conjointement avec un TestScope/TestDispatcher : avancer
 * `fakeClock.advance(ms)` ET `testScheduler.advanceTimeBy(ms)` pour rester
 * cohérent (le clock contrôle ce que les machines lisent ; le scheduler
 * contrôle quand `delay()` se résout).
 */
class FakeClock(initial: Long = 0L) : Clock {
    var current: Long = initial
        private set

    override fun nowMillis(): Long = current

    fun advance(ms: Long) {
        current += ms
    }

    fun set(ms: Long) {
        current = ms
    }
}
