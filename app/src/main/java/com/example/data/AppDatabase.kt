package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.CallEventDao
import com.example.data.dao.ContactRuleDao
import com.example.data.dao.OutboxSmsDao
import com.example.data.dao.TriggerHistoryDao
import com.example.data.entity.CallEventEntity
import com.example.data.entity.ContactRuleEntity
import com.example.data.entity.OutboxSmsEntity
import com.example.data.entity.TriggerHistoryEntity

@Database(
    entities = [
        CallEventEntity::class,
        ContactRuleEntity::class,
        TriggerHistoryEntity::class,
        OutboxSmsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callEventDao(): CallEventDao
    abstract fun contactRuleDao(): ContactRuleDao
    abstract fun triggerHistoryDao(): TriggerHistoryDao
    abstract fun outboxSmsDao(): OutboxSmsDao

    companion object {
        private const val TAG = "AppDatabase"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Running Migration 1 -> 2: Adding isTest columns and purging legacy seeded contacts")
                // 1. Add isTest columns with default 0
                db.execSQL("ALTER TABLE call_events ADD COLUMN isTest INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_call_events_isTest ON call_events(isTest)")

                db.execSQL("ALTER TABLE trigger_history ADD COLUMN isTest INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trigger_history_isTest ON trigger_history(isTest)")

                db.execSQL("ALTER TABLE outbox_sms ADD COLUMN isTest INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_sms_isTest ON outbox_sms(isTest)")

                // 2. Delete legacy seeded rows explicitly while preserving all user contacts
                val legacyNumbers = listOf(
                    "+91" + "98765" + "43210",
                    "+91" + "98765" + "43211",
                    "112"
                )
                val inClause = legacyNumbers.joinToString("','", "'", "'")
                db.execSQL("DELETE FROM contact_rules WHERE normalizedNumber IN ($inClause)")
                db.execSQL("DELETE FROM call_events WHERE normalizedNumber IN ($inClause)")
                db.execSQL("DELETE FROM trigger_history WHERE callerNumber IN ($inClause)")
                db.execSQL("DELETE FROM outbox_sms WHERE recipientNumber IN ($inClause)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "missed_call_sos.db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
