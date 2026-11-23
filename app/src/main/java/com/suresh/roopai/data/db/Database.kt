package com.suresh.roopai.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

/** One saved edit: the project folder id plus its serialized edit stack. */
@Entity(tableName = "edits")
data class EditRecord(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "stack_json") val stackJson: String,
    @ColumnInfo(name = "op_count") val opCount: Int = 0,
    @ColumnInfo(name = "exported_uri") val exportedUri: String? = null,
)

@Dao
interface EditDao {
    @Query("SELECT * FROM edits ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<EditRecord>>

    @Query("SELECT * FROM edits WHERE id = :id")
    suspend fun get(id: String): EditRecord?

    @Query("SELECT id FROM edits")
    suspend fun ids(): List<String>

    @Upsert
    suspend fun upsert(record: EditRecord)

    @Query("UPDATE edits SET stack_json = :stackJson, op_count = :opCount, updated_at = :now WHERE id = :id")
    suspend fun updateStack(id: String, stackJson: String, opCount: Int, now: Long): Int

    @Query("UPDATE edits SET exported_uri = :uri, updated_at = :now WHERE id = :id")
    suspend fun setExported(id: String, uri: String, now: Long)

    @Query("DELETE FROM edits WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM edits")
    suspend fun deleteAll()
}

@Database(entities = [EditRecord::class], version = RoopDatabase.VERSION, exportSchema = true)
abstract class RoopDatabase : RoomDatabase() {
    abstract fun edits(): EditDao

    companion object {
        const val VERSION = 1
        const val NAME = "roopai.db"

        /**
         * Migration scaffold. Schema JSON for every version is exported to `app/schemas`.
         * When the schema changes: bump [VERSION], add `Migration(n, n + 1) { db -> ... }` here
         * (or an `AutoMigration` in the annotation) and keep the old schema files in git.
         * Destructive fallback is deliberately not enabled - user edits must survive updates.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
