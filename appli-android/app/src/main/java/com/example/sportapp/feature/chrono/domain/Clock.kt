package com.example.sportapp.feature.chrono.domain

/**
 * Source de temps monotone (millisecondes). Abstraite pour permettre
 * d'injecter un `FakeClock` controllable dans les tests JVM des
 * state machines (`StopwatchStateMachine`, `TimerStateMachine`).
 */
interface Clock {
    fun nowMillis(): Long
}
