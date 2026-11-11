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
import com.jkhx.player.core.database.dao.LocalLibraryDao
import com.jkhx.player.core.database.entity.LocalLibraryEntity
import com.jkhx.player.core.database.dao.RemoteServerDao
import com.jkhx.player.core.database.entity.RemoteServerEntity
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

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `local_library` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                `subtitleUri` TEXT,
                `coverUrl` TEXT,
                `author` TEXT,
                `description` TEXT,
                `chapterCount` INTEGER NOT NULL DEFAULT 1,
                `episodesJson` TEXT NOT NULL,
                `addedAt` INTEGER NOT NULL,
                `lastOpenedAt` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_library_type` ON `local_library` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_library_lastOpenedAt` ON `local_library` (`lastOpenedAt`)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `remote_servers` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `protocol` TEXT NOT NULL,
                `host` TEXT NOT NULL,
                `port` INTEGER NOT NULL,
                `useSsl` INTEGER NOT NULL DEFAULT 0,
                `basePath` TEXT NOT NULL DEFAULT '/',
                `username` TEXT,
                `password` TEXT,
                `isAnonymous` INTEGER NOT NULL DEFAULT 0,
                `addedAt` INTEGER NOT NULL,
                `lastConnectedAt` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_servers_protocol` ON `remote_servers` (`protocol`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_servers_addedAt` ON `remote_servers` (`addedAt`)")
    }
}

@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
        PluginEntity::class,
        RepositoryEntity::class,
        StremioAddonEntity::class,
        LocalLibraryEntity::class,
        RemoteServerEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun pluginDao(): PluginDao
    abstract fun repositoryDao(): RepositoryDao
    abstract fun stremioAddonDao(): StremioAddonDao
    abstract fun localLibraryDao(): LocalLibraryDao
    abstract fun remoteServerDao(): RemoteServerDao

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
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

