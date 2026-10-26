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

import com.jkhx.player.core.database.dao.RepositoryDao
import com.jkhx.player.core.database.entity.RepositoryEntity
import com.jkhx.player.core.database.dao.StremioAddonDao
import com.jkhx.player.core.database.entity.StremioAddonEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `stremio_addons` (
                `url` TEXT NOT NULL PRIMARY KEY,
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `version` TEXT NOT NULL DEFAULT '1.0.0',
                `description` TEXT,
                `iconUrl` TEXT,
                `manifestJson` TEXT NOT NULL,
                `isEnabled` INTEGER NOT NULL DEFAULT 1,
                `orderIndex` INTEGER NOT NULL DEFAULT 0,
                `addedAt` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
        PluginEntity::class,
        RepositoryEntity::class,
        StremioAddonEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun pluginDao(): PluginDao
    abstract fun repositoryDao(): RepositoryDao
    abstract fun stremioAddonDao(): StremioAddonDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jkhx_player.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

