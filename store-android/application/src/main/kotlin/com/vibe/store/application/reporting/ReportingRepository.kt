package com.vibe.store.application.reporting

import com.vibe.store.api.DocumentModel
import com.vibe.store.api.DashboardRequest
import com.vibe.store.api.ReportFilter

/** Read-only projection port. Booleans are derived by ReportingAuthority under the identity lease. */
interface ReportingRepository {
    suspend fun snapshot(filter: ReportFilter): DocumentModel
    suspend fun dashboard(filter: ReportFilter, includePresence: Boolean): DocumentModel
    suspend fun dashboard(request: DashboardRequest, includePresence: Boolean): DocumentModel = dashboard(
        ReportFilter(com.vibe.store.api.ReportTab.SUMMARY, request.asOfDate, request.asOfDate.plusDays(1),
            rowLimit = request.rowLimit, timeZoneId = request.timeZoneId), includePresence
    )
}
