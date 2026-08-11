package com.example.sportapp.feature.chrono

import android.content.Context
import com.example.sportapp.feature.chrono.data.ChronoSettingsDataStore
import com.example.sportapp.feature.chrono.domain.Clock
import com.example.sportapp.feature.chrono.domain.SystemClockImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChronoProvidesModule {

    @Provides
    @Singleton
    fun provideChronoSettingsDataStore(@ApplicationContext context: Context): ChronoSettingsDataStore {
        return ChronoSettingsDataStore(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ChronoBindsModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClockImpl): Clock
}
