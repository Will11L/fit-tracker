package com.example.sportapp.feature.onboarding

import android.content.Context
import com.example.sportapp.feature.onboarding.data.OnboardingDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OnboardingModule {

    @Provides
    @Singleton
    fun provideOnboardingDataStore(@ApplicationContext context: Context): OnboardingDataStore {
        return OnboardingDataStore(context)
    }
}
