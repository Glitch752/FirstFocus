package app.enlily.focus.accessibility

import app.enlily.focus.data.local.AppDao
import app.enlily.focus.data.local.TemporaryAllowanceEntity

/** Holds data about allowances and whether apps may currently be used. */
class AllowanceRepository(private val dao: AppDao) {
    suspend fun hasActiveAllowance(packageName: String, now: Long = System.currentTimeMillis()): Boolean =
        dao.activeAllowance(packageName, now) != null

    suspend fun grantAllowance(packageName: String, durationMillis: Long): Long {
        val expiresAt = System.currentTimeMillis() + durationMillis
        dao.upsertAllowance(
            TemporaryAllowanceEntity(
                packageName = packageName,
                expiresAtMillis = expiresAt
            )
        )
        return expiresAt
    }

    suspend fun getAllowanceExpiration(packageName: String, now: Long = System.currentTimeMillis()): Long? =
        dao.activeAllowance(packageName, now)?.expiresAtMillis
}
