package com.example.sportapp.feature.chrono.domain

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemClockImpl @Inject constructor() : Clock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}
