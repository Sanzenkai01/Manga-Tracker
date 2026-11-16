package com.sanzenkai.mangatracker.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanzenkai.mangatracker.core.data.local.entity.CheckAttemptEntity
import com.sanzenkai.mangatracker.core.domain.model.MonitoringProblemSignals
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckAttemptDao {
    @Query(
        """
        SELECT * FROM check_attempt
        ORDER BY mangaId, startedAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun getAllForBackup(): List<CheckAttemptEntity>

    @Query(
        """
        SELECT * FROM check_attempt
        WHERE mangaId = :mangaId
        ORDER BY startedAtEpochMillis DESC
        """,
    )
    fun observeForManga(mangaId: String): Flow<List<CheckAttemptEntity>>

    @Query(
        """
        WITH latest_success AS (
            SELECT mangaId, MAX(COALESCE(finishedAtEpochMillis, startedAtEpochMillis)) AS lastSuccessAtEpochMillis
            FROM check_attempt
            WHERE outcome = 'SUCCESS'
            GROUP BY mangaId
        ),
        site_failures AS (
            SELECT
                attempt.mangaId AS mangaId,
                attempt.id AS id,
                COALESCE(attempt.finishedAtEpochMillis, attempt.startedAtEpochMillis) AS failedAtEpochMillis
            FROM check_attempt AS attempt
            LEFT JOIN latest_success
                ON latest_success.mangaId = attempt.mangaId
            WHERE attempt.outcome IN ('FAILURE', 'INCOMPLETE_LIST')
                AND attempt.errorCode IN (
                    'ACCESS_BLOCKED',
                    'RATE_LIMITED',
                    'PAGE_UNAVAILABLE',
                    'SERVER_ERROR',
                    'HTTP_ERROR',
                    'REDIRECT_BLOCKED',
                    'TOO_MANY_REDIRECTS',
                    'NOT_HTML',
                    'NOT_JSON',
                    'EMPTY_BODY',
                    'RESPONSE_TOO_LARGE',
                    'INVALID_HTML',
                    'UNSUPPORTED_DYNAMIC_CONTENT',
                    'INCOMPLETE_CHAPTER_LIST',
                    'NO_MATCH'
                )
                AND (
                    latest_success.lastSuccessAtEpochMillis IS NULL
                    OR COALESCE(attempt.finishedAtEpochMillis, attempt.startedAtEpochMillis)
                        > latest_success.lastSuccessAtEpochMillis
                )
        )
        SELECT
            manga.id AS mangaId,
            COUNT(site_failures.id) AS siteFailureCount,
            latest_success.lastSuccessAtEpochMillis AS lastSuccessAtEpochMillis,
            (
                SELECT id
                FROM site_failures
                WHERE mangaId = manga.id
                ORDER BY failedAtEpochMillis ASC, id ASC
                LIMIT 1
            ) AS episodeId,
            MIN(site_failures.failedAtEpochMillis) AS firstSiteFailureAtEpochMillis
        FROM manga
        LEFT JOIN latest_success ON latest_success.mangaId = manga.id
        LEFT JOIN site_failures ON site_failures.mangaId = manga.id
        WHERE (:mangaId IS NULL OR manga.id = :mangaId)
        GROUP BY manga.id
        """,
    )
    fun observeMonitoringProblemSignals(mangaId: String? = null): Flow<List<MonitoringProblemSignals>>

    @Query("DELETE FROM check_attempt WHERE mangaId IN (:mangaIds)")
    suspend fun deleteForMangaIds(mangaIds: List<String>): Int

    @Insert
    suspend fun insert(attempt: CheckAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfMissing(attempts: List<CheckAttemptEntity>)
}
