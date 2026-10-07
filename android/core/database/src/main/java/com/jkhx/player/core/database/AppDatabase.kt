package com.jkhx.player.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jkhx.player.core.database.dao.FavoriteDao
import com.jkhx.player.core.database.dao.HistoryDao
import com.jkhx.player.core.database.dao.PlaybackProgressDao
import com.jkhx.player.core.database.dao.PluginDao
import com.jkhx.player.core.database.entity.FavoriteEntity
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.core.database.entity.PlaybackProgressEntity
import com.jkhx.player.core.database.entity.PluginEntity

@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
        PluginEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun pluginDao(): PluginDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jkhx_player.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

