package com.example.sportapp.core.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServerUrlModule {

    @Provides
    @Singleton
    fun provideServerUrlDataStore(@ApplicationContext context: Context): ServerUrlDataStore {
        return ServerUrlDataStore(context)
    }
}
