package io.github.tukiza7_debug.vibecast.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: Int,
    val json: String,
    val enabled: Boolean,
    val source: String,
    val sourceRef: String?,
    val gitPath: String?,
    val repoOwner: String?,
    val repoName: String?,
    val addedAt: Long,
    val lastError: String?,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val itemKey: String,
    val extensionId: String,
    val title: String,
    val url: String,
    val cover: String?,
    val addedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val itemKey: String,
    val extensionId: String,
    val title: String,
    val url: String,
    val cover: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions WHERE id = :id")
    suspend fun byId(id: String): ExtensionEntity?

    @Query("SELECT * FROM extensions WHERE enabled = 1")
    suspend fun enabledList(): List<ExtensionEntity>

    @Upsert
    suspend fun upsert(extension: ExtensionEntity)

    @Query("DELETE FROM extensions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE extensions SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE itemKey = :key")
    suspend fun byKey(key: String): FavoriteEntity?

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE itemKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updatedAt DESC LIMIT 100")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE itemKey = :key")
    suspend fun byKey(key: String): HistoryEntity?

    @Upsert
    suspend fun upsert(entry: HistoryEntity)
}

@Database(
    entities = [ExtensionEntity::class, FavoriteEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun extensionDao(): ExtensionDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun historyDao(): HistoryDao
}
