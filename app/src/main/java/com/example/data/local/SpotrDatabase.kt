package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SightingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SpotrDatabase : RoomDatabase() {

    abstract fun sightingDao(): SightingDao

    companion object {
        @Volatile
        private var INSTANCE: SpotrDatabase? = null

        fun getInstance(context: Context): SpotrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpotrDatabase::class.java,
                    "spotr_local.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
