package com.myra.ai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val convId: Long,
    val fromUser: Boolean,
    val text: String,
    val ts: Long = System.currentTimeMillis(),
    val attachName: String? = null
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val ts: Long = System.currentTimeMillis()
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String
)

@Dao
interface MyraDao {
    @Insert suspend fun insertMessage(m: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE convId = :c ORDER BY id")
    fun conv(c: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM messages WHERE convId = :c ORDER BY id DESC LIMIT :n) ORDER BY id")
    suspend fun recent(c: Long, n: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE text LIKE '%' || :q || '%' ORDER BY id DESC LIMIT 50")
    suspend fun searchMessages(q: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE ts BETWEEN :a AND :b ORDER BY id")
    suspend fun between(a: Long, b: Long): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY id")
    suspend fun allMessages(): List<MessageEntity>

    @Query("SELECT MAX(convId) FROM messages")
    suspend fun lastConv(): Long?

    @Query("DELETE FROM messages")
    suspend fun clearMessages()

    @Insert suspend fun insertMemory(m: MemoryEntity): Long
    @Update suspend fun updateMemory(m: MemoryEntity)
    @Delete suspend fun deleteMemory(m: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY id DESC")
    fun memories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY id")
    suspend fun allMemoriesOnce(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE text LIKE '%' || :q || '%' ORDER BY id DESC")
    suspend fun searchMemories(q: String): List<MemoryEntity>

    @Query("DELETE FROM memories")
    suspend fun clearMemories()

    @Insert suspend fun insertPrompt(p: PromptEntity): Long
    @Delete suspend fun deletePrompt(p: PromptEntity)

    @Query("SELECT * FROM prompts ORDER BY id DESC")
    fun prompts(): Flow<List<PromptEntity>>
}

@Database(entities = [MessageEntity::class, MemoryEntity::class, PromptEntity::class], version = 1, exportSchema = false)
abstract class MyraDb : RoomDatabase() {
    abstract fun dao(): MyraDao
}
