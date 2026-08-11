package com.example.sportapp.core.data.repository

import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.core.utils.CustomDateUtils
import com.example.sportapp.core.data.local.MuscleGoalDao
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.parseTargetMinimum
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuscleGoalsManager @Inject constructor(
    private val muscleGoalDao: MuscleGoalDao,
    private val actualWorkoutSetDao: ActualWorkoutSetDao,

    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager
) {

    suspend fun updateMuscleGoalsForWeek(weekISO: String) {
        val goals = muscleGoalDao.getGoalsForWeek(weekISO)
        val startOfWeek = CustomDateUtils.getStartOfWeek(weekISO)
        val endOfWeek = CustomDateUtils.getEndOfWeek(weekISO)

        goals.forEach { goal ->
            val doneCount = actualWorkoutSetDao.getDoneSetsForMuscleInWeek(
                muscleUUID = goal.muscleUUID,
                startOfWeek = startOfWeek,
                endOfWeek = endOfWeek
            )

            val newStatus = if (doneCount >= parseTargetMinimum(goal.target)) "DONE" else goal.status

            muscleGoalDao.updateDoneCount(
                uuid = goal.uuid,
                done = doneCount,
                status = newStatus
            )

        }

        syncEngine.pushEntityClass(MuscleGoal::class)
    }
}
