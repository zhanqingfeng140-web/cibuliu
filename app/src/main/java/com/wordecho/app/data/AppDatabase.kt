package com.wordecho.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Word::class, AnswerLog::class, BatchWord::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wordDao(): WordDao
    abstract fun batchWordDao(): BatchWordDao
    abstract fun logDao(): AnswerLogDao

    companion object {

        /** v1 → v2：加入艾宾浩斯排期字段 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN reviewStage INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN nextReviewAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3：加入个人笔记与形近词 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE words ADD COLUMN confusables TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3 → v4：单词归属到「某一天导入的批次」 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN batchDate TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 → v5：同一天里区分「第几次上传」 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN batchId INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v5 → v6：批次归属从 words 表拆到 batch_words 表。
         * 这样同一个词可以出现在多次上传里，重复上传不会被去重掉。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `batch_words` (" +
                        "`batchDate` TEXT NOT NULL, " +
                        "`batchId` INTEGER NOT NULL, " +
                        "`wordId` INTEGER NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`batchDate`, `batchId`, `wordId`))"
                )
                // 把已有数据回填进新表
                db.execSQL(
                    "INSERT OR IGNORE INTO batch_words(batchDate, batchId, wordId, addedAt) " +
                        "SELECT batchDate, batchId, id, 0 FROM words WHERE batchDate != ''"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "wordecho.db"
            )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
