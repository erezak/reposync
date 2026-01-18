package com.erez.reposync.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.erez.reposync.data.db.dao.FingerprintDao
import com.erez.reposync.data.db.dao.ProfileDao
import com.erez.reposync.data.db.dao.SyncLogDao
import com.erez.reposync.data.db.entities.FingerprintEntity
import com.erez.reposync.data.db.entities.ProfileEntity
import com.erez.reposync.data.db.entities.SyncLogEntity

@Database(
    entities = [ProfileEntity::class, FingerprintEntity::class, SyncLogEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun fingerprintDao(): FingerprintDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN httpsUsername TEXT NOT NULL DEFAULT 'token'")
            }
        }
    }
}
