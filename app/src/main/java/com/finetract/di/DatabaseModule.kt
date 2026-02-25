package com.finetract.di

import android.content.Context
import androidx.room.Room
import com.finetract.data.local.FinetractDatabase
import com.finetract.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FinetractDatabase {
        return Room.databaseBuilder(
            context,
            FinetractDatabase::class.java,
            "finetract_db"
        ).build()
    }

    @Provides
    fun provideTransactionDao(db: FinetractDatabase): TransactionDao {
        return db.transactionDao()
    }
}
