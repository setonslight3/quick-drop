package com.quickdrop.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "subjects",
    indices = [Index(value = ["archived_at_epoch_ms"])]
)
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color_key: String,
    val icon_key: String,
    val term: String?,
    val instructor: String?,
    val archived_at_epoch_ms: Long?,
    val created_at_epoch_ms: Long,
    val updated_at_epoch_ms: Long
)

@Entity(
    tableName = "class_slots",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subject_id", "enabled"])]
)
data class ClassSlotEntity(
    @PrimaryKey val id: String,
    val subject_id: String,
    val weekday_mask: Int,
    val start_minute_of_day: Int,
    val end_minute_of_day: Int,
    val zone_id: String,
    val location: String,
    val enabled: Boolean,
    val reminder_enabled: Boolean,
    val reminder_lead_minutes: Int,
    val created_at_epoch_ms: Long,
    val updated_at_epoch_ms: Long
)

@Entity(
    tableName = "schedule_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = ClassSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slot_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["slot_id", "original_local_date_iso"], unique = true)]
)
data class ScheduleExceptionEntity(
    @PrimaryKey val id: String,
    val slot_id: String,
    val original_local_date_iso: String,
    val action: String, // CANCEL or MOVE
    val replacement_local_date_iso: String?,
    val replacement_start_minute: Int?,
    val replacement_end_minute: Int?,
    val replacement_location: String?,
    val created_at_epoch_ms: Long
)

@Entity(
    tableName = "lectures",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subject_id", "local_date_iso"])]
)
data class LectureEntity(
    @PrimaryKey val id: String,
    val subject_id: String,
    val title: String,
    val local_date_iso: String,
    val topic_label: String?,
    val notes: String,
    val status: String,
    val created_at_epoch_ms: Long,
    val updated_at_epoch_ms: Long
)

@Entity(
    tableName = "source_files",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lecture_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["lecture_id"]), Index(value = ["sha256"])]
)
data class SourceFileEntity(
    @PrimaryKey val id: String,
    val lecture_id: String,
    val display_name: String,
    val mime_type: String,
    val size_bytes: Long,
    val sha256: String,
    val private_relative_path: String,
    val uri_permission_held: Boolean,
    val extraction_state: String,
    val page_or_slide_count: Int,
    val error_code: String?,
    val partial_extraction_notice: String?,
    val created_at_epoch_ms: Long
)

@Entity(
    tableName = "source_chunks",
    foreignKeys = [
        ForeignKey(
            entity = SourceFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["file_id", "ordinal"])]
)
data class SourceChunkEntity(
    @PrimaryKey val id: String,
    val file_id: String,
    val anchor_kind: String,
    val anchor_index: Int,
    val ordinal: Int,
    val extracted_text: String,
    val token_estimate: Int,
    val content_hash: String
)

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subject_id", "canonical_key"], unique = true)]
)
data class TopicEntity(
    @PrimaryKey val id: String,
    val subject_id: String,
    val parent_topic_id: String?,
    val name: String,
    val canonical_key: String,
    val core_weight: Double,
    val created_at_epoch_ms: Long,
    val archived_at_epoch_ms: Long?
)

@Entity(
    tableName = "learning_items",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topic_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lecture_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["topic_id", "approval_status"]),
        Index(value = ["lecture_id"]),
        Index(value = ["content_hash"])
    ]
)
data class LearningItemEntity(
    @PrimaryKey val id: String,
    val topic_id: String,
    val lecture_id: String?,
    val kind: String,
    val prompt_or_body: String,
    val explanation: String,
    val difficulty: String,
    val source_file_id: String?,
    val source_chunk_id: String?,
    val anchor_kind: String?,
    val anchor_index: Int?,
    val evidence_quote: String,
    val approval_status: String,
    val is_manual_or_retained: Boolean,
    val confidence_label: String,
    val version: Int,
    val content_hash: String,
    val created_at_epoch_ms: Long,
    val updated_at_epoch_ms: Long
)

@Entity(
    tableName = "answer_options",
    foreignKeys = [
        ForeignKey(
            entity = LearningItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["item_id", "ordinal"])]
)
data class AnswerOptionEntity(
    @PrimaryKey val id: String,
    val item_id: String,
    val ordinal: Int,
    val body: String,
    val is_correct: Boolean
)

@Entity(
    tableName = "quiz_sessions",
    indices = [Index(value = ["status"]), Index(value = ["started_at_epoch_ms"])]
)
data class QuizSessionEntity(
    @PrimaryKey val id: String,
    val scope_snapshot_json: String,
    val item_ids_json: String,
    val current_question_index: Int,
    val status: String,
    val started_at_epoch_ms: Long,
    val ended_at_epoch_ms: Long?,
    val correct_count: Int,
    val answered_count: Int,
    val skipped_count: Int
)

@Entity(
    tableName = "quiz_responses",
    foreignKeys = [
        ForeignKey(
            entity = QuizSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id", "sequence"], unique = true), Index(value = ["item_id"])]
)
data class QuizResponseEntity(
    @PrimaryKey val id: String,
    val session_id: String,
    val item_id: String,
    val item_version: Int,
    val topic_id: String,
    val sequence: Int,
    val selected_option_id: String?,
    val outcome: String,
    val latency_ms: Long,
    val answered_at_epoch_ms: Long,
    val confidence: String,
    val prompt_snapshot: String,
    val explanation_snapshot: String,
    val source_citation_snapshot: String,
    val options_snapshot_json: String
)

@Entity(
    tableName = "topic_review_states",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topic_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TopicReviewStateEntity(
    @PrimaryKey val topic_id: String,
    val stability_days: Double,
    val difficulty_score: Double,
    val due_at_epoch_ms: Long?,
    val last_review_epoch_ms: Long?,
    val repetitions: Int,
    val lapses: Int,
    val correct_count: Int,
    val attempt_count: Int,
    val last_outcome: String?,
    val distinct_correct_days_json: String,
    val has_seven_day_spaced_correct: Boolean,
    val paused: Boolean
)

@Entity(
    tableName = "drop_states",
    foreignKeys = [
        ForeignKey(
            entity = LearningItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["learning_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DropStateEntity(
    @PrimaryKey val learning_item_id: String,
    val visibility_state: String,
    val saved_at_epoch_ms: Long?,
    val last_seen_at_epoch_ms: Long?
)

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey val pref_key: String,
    val pref_value: String,
    val updated_at_epoch_ms: Long
)

@Entity(
    tableName = "ai_jobs",
    indices = [Index(value = ["lecture_id"]), Index(value = ["state"])]
)
data class AiJobEntity(
    @PrimaryKey val id: String,
    val job_type: String,
    val lecture_id: String?,
    val source_file_ids_json: String,
    val state: String,
    val attempts: Int,
    val model_id: String,
    val prompt_version: String,
    val created_at_epoch_ms: Long,
    val updated_at_epoch_ms: Long,
    val error_code: String,
    val retry_after_seconds: Int?,
    val sanitized_status_message: String
)

@Dao
interface QuickDropDao {
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun observeSubjects(): Flow<List<SubjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubject(entity: SubjectEntity)

    @Query("SELECT * FROM class_slots")
    fun observeClassSlots(): Flow<List<ClassSlotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClassSlot(entity: ClassSlotEntity)

    @Query("SELECT * FROM schedule_exceptions")
    fun observeScheduleExceptions(): Flow<List<ScheduleExceptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduleException(entity: ScheduleExceptionEntity)

    @Query("SELECT * FROM lectures WHERE subject_id = :subjectId ORDER BY local_date_iso DESC")
    fun observeLecturesForSubject(subjectId: String): Flow<List<LectureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLecture(entity: LectureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceFile(entity: SourceFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<SourceChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopic(entity: TopicEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLearningItem(entity: LearningItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswerOptions(options: List<AnswerOptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuizSession(entity: QuizSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuizResponse(entity: QuizResponseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopicReviewState(entity: TopicReviewStateEntity)

    @Transaction
    suspend fun completeQuizAtomically(
        completedSession: QuizSessionEntity,
        updatedTopicStates: List<TopicReviewStateEntity>
    ) {
        upsertQuizSession(completedSession)
        for (state in updatedTopicStates) {
            upsertTopicReviewState(state)
        }
    }
}

@Database(
    entities = [
        SubjectEntity::class,
        ClassSlotEntity::class,
        ScheduleExceptionEntity::class,
        LectureEntity::class,
        SourceFileEntity::class,
        SourceChunkEntity::class,
        TopicEntity::class,
        LearningItemEntity::class,
        AnswerOptionEntity::class,
        QuizSessionEntity::class,
        QuizResponseEntity::class,
        TopicReviewStateEntity::class,
        DropStateEntity::class,
        AppPreferenceEntity::class,
        AiJobEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class QuickDropDatabase : RoomDatabase() {
    abstract fun quickDropDao(): QuickDropDao

    companion object {
        /**
         * Explicit non-destructive migration from v1 to v2 adding sync-compatible metadata index
         * without ever calling fallbackToDestructiveMigration().
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_quiz_sessions_started_at_epoch_ms` ON `quiz_sessions` (`started_at_epoch_ms`)"
                )
            }
        }
    }
}
