package com.vibe.store.presentation

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibe.store.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Presentation-only state. Server permissions remain authoritative. */
@Composable
fun TeamScreen(
    service: TeamService,
    permissions: Set<String>,
    actorId: Long,
    actorRole: String,
    french: Boolean,
    initialTab: String,
    pickPhoto: ((SelectedImage?) -> Unit) -> Unit,
    navigationLock: (Boolean) -> Unit = {},
    back: () -> Unit,
) {
    fun t(fr: String, en: String) = if (french) fr else en
    val canReadStaff = "EMPLOYEES:READ" in permissions
    val canWriteStaff = "EMPLOYEES:UPDATE" in permissions
    val canReadPresence = "PRESENCE:READ" in permissions
    val canCorrect = "PRESENCE:UPDATE" in permissions
    var tab by remember(service, actorId, initialTab) { mutableStateOf(initialTab) }
    var staffSearch by remember { mutableStateOf("") }
    var staffRole by remember { mutableStateOf("") }
    var staffOffset by remember { mutableIntStateOf(0) }
    var staff by remember { mutableStateOf<List<EmployeeSummary>>(emptyList()) }
    var daySearch by remember { mutableStateOf("") }
    var dayRole by remember { mutableStateOf("") }
    var dayOffset by remember { mutableIntStateOf(0) }
    var today by remember { mutableStateOf(DailyAttendancePage(emptyList(), false)) }
    var historyFrom by remember { mutableStateOf("") }
    var historyTo by remember { mutableStateOf("") }
    var historySigner by remember { mutableStateOf("") }
    var historyOffset by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(AttendancePage(emptyList(), 0, false)) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<EmployeeDetail?>(null) }
    var editing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf<DailyAttendanceRow?>(null) }
    var correcting by remember { mutableStateOf<AttendanceView?>(null) }
    var temporary by remember { mutableStateOf<TemporaryPassword?>(null) }
    val scope = rememberCoroutineScope()
    val blockNavigation = loading || editing || signing != null || correcting != null || temporary != null
    SideEffect { navigationLock(blockNavigation) }
    DisposableEffect(Unit) { onDispose { navigationLock(false) } }

    fun run(action: suspend () -> Unit) {
        if (loading) return
        loading = true; error = null; info = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: TeamFailure) {
                error = when (failure.code) {
                    TeamError.INVALID_CREDENTIALS -> t("Mot de passe incorrect ou accès temporairement verrouillé.", "Invalid password or temporarily locked access.")
                    TeamError.FORBIDDEN -> t("Opération non autorisée.", "Operation not authorized.")
                    TeamError.CASH_OPEN -> t("Fermez la caisse avant de modifier ce compte.", "Close the cash session before changing this account.")
                    TeamError.CONFLICT -> t("Conflit : rechargez les données.", "Conflict: reload the data.")
                    TeamError.INVALID_TRANSITION -> t("Transition de présence impossible. Actualisez la fiche.", "Attendance transition denied. Refresh the sheet.")
                    TeamError.INVALID_INTERVAL -> t("Intervalle horaire invalide.", "Invalid time interval.")
                    TeamError.MEDIA_TOO_LARGE -> t("Photo limitée à 512 Kio.", "Photo limited to 512 KiB.")
                    TeamError.INVALID_MEDIA, TeamError.MEDIA_UNAVAILABLE -> t("Photo non valide ou inaccessible.", "Invalid or unavailable photo.")
                    else -> t("Vérifiez les champs et réessayez.", "Check the fields and retry.")
                }
            }
            catch (_: SecurityFailure) { error = t("Accès refusé ou session expirée.", "Access denied or session expired.") }
            catch (_: Exception) { error = t("Opération indisponible. Aucun succès n'est confirmé.", "Operation unavailable. No success is confirmed.") }
            finally { loading = false }
        }
    }

    // Separate requests and cancellation scopes; reloading cannot record a presence.
    LaunchedEffect(service, actorId, tab, staffSearch, staffRole, staffOffset, revision) {
        if (tab == "employees" && canReadStaff && !editing) {
            try {
                staff = service.employees(EmployeeFilter(search = staffSearch, role = staffRole, limit = 41, offset = staffOffset))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { staff = emptyList(); error = t("Liste indisponible.", "List unavailable.") }
        }
    }
    LaunchedEffect(service, actorId, tab, daySearch, dayRole, dayOffset, revision) {
        if (tab == "today" && canReadPresence) {
            try {
                today = service.today(DailyAttendanceFilter(search = daySearch, role = dayRole, limit = 40, offset = dayOffset))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { today = DailyAttendancePage(emptyList(), false); error = t("Fiche indisponible.", "Sheet unavailable.") }
        }
    }
    LaunchedEffect(service, actorId, tab, historyFrom, historyTo, historySigner, historyOffset, revision) {
        if (tab == "history" && canReadPresence) {
            try {
                history = service.attendance(AttendanceFilter(
                    from = historyFrom, to = historyTo,
                    signerId = historySigner.toLongOrNull(), limit = 40, offset = historyOffset,
                ))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { history = AttendancePage(emptyList(), 0, false); error = t("Historique indisponible.", "History unavailable.") }
        }
    }
    fun goBack() {
        if (loading) return
        when {
            editing -> { editing = false; creating = false; detail = null }
            tab == "history" -> tab = "today"
            else -> back()
        }
    }
    BackHandler { goBack() }
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val columns = if (windowWidth < 600.dp) 1 else if (windowWidth < 840.dp) 2 else 3
    val widthLabel = when (columns) { 1 -> "COMPACT"; 2 -> "MEDIUM"; else -> "EXPANDED" }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(SpatialTokens.compactInset)
        .testTag("team-layout-$widthLabel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("STORE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(if (editing || tab == "employees") t("Équipe", "Team") else t("Présences", "Attendance"),
                    style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(enabled = !loading, onClick = ::goBack) { Text(t("Retour", "Back")) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.testTag("team-error"), color = MaterialTheme.colorScheme.error) }
        info?.let { Text(it, Modifier.testTag("team-message")) }
        if (!editing) {
            val destinations = buildList {
                if (canReadStaff) add("employees")
                if (canReadPresence) { add("today"); add("history") }
            }
            if (destinations.isNotEmpty()) TabRow(selectedTabIndex = destinations.indexOf(tab).coerceAtLeast(0)) {
                for (destination in destinations) {
                    Tab(selected = tab == destination, onClick = { tab = destination },
                        modifier = Modifier.testTag(when (destination) {
                            "employees" -> "team-tab"; "today" -> "today-tab"; else -> "history-tab"
                        }),
                        text = { Text(when (destination) {
                            "employees" -> t("Équipe", "Team")
                            "today" -> t("Aujourd'hui", "Today")
                            else -> t("Historique", "History")
                        }) })
                }
            }
        }
        if (editing && canReadStaff) {
            key(detail?.accountId, creating) {
                EmployeeEditor(service, detail, creating, canWriteStaff, actorRole, french, pickPhoto,
                    onSaved = { saved, secret ->
                        detail = saved; editing = false; creating = false; revision++
                        if (secret != null) temporary = secret else info = t("Fiche enregistrée.", "Profile saved.")
                    }, onBack = { editing = false; creating = false; detail = null })
            }
        } else when (tab) {
            "employees" -> if (canReadStaff) {
                LazyColumn(Modifier.fillMaxSize().testTag("team-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        TeamSectionHeading(t("Collaborateurs", "People"),
                            t("Comptes, rôles et situation professionnelle", "Accounts, roles and employment status"))
                        Spacer(Modifier.height(10.dp))
                        TeamField(t("Rechercher un employé", "Search employees"), staffSearch, "team-search", loading) { staffSearch = it; staffOffset = 0 }
                        Text(t("Filtrer par rôle", "Filter by role"), style = MaterialTheme.typography.labelMedium)
                        TeamRoles(staffRole, true, actorRole, french) { staffRole = it; staffOffset = 0 }
                        if (canWriteStaff) Button(modifier = Modifier.testTag("team-create"), enabled = !loading, onClick = {
                            detail = null; creating = true; editing = true
                        }) { Text(t("+ Nouvel employé", "+ New employee")) }
                        if (staff.isEmpty()) TeamEmptyState(t("Aucun employé pour ces filtres.", "No employees match these filters."))
                    }
                    items(staff.take(40).chunked(columns), key = { it.first().accountId }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { employee ->
                                TeamEmployeeCard(employee, french, loading, Modifier.weight(1f)) {
                                    run { detail = service.employee(employee.accountId); creating = false; editing = true }
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    item { TeamPager(staffOffset, 40, staff.size > 40, french) { staffOffset = it } }
                }
            }
            "today" -> if (canReadPresence) {
                LazyColumn(Modifier.fillMaxSize().testTag("today-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        TeamSectionHeading(t("Présences du jour", "Today's attendance"),
                            t("Pointages de la journée et actions individuelles", "Daily attendance and individual actions"))
                        Spacer(Modifier.height(10.dp))
                        Surface(shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(t("Sur cette page", "On this page"), style = MaterialTheme.typography.labelMedium)
                                Text(t("${today.rows.count { it.attendance?.endTime == null && it.attendance != null }} en service  ·  ${today.rows.count { it.attendance?.endTime != null }} sorties",
                                    "${today.rows.count { it.attendance?.endTime == null && it.attendance != null }} clocked in  ·  ${today.rows.count { it.attendance?.endTime != null }} clocked out"),
                                    style = MaterialTheme.typography.titleMedium)
                                Text(t("Chaque pointage nécessite le mot de passe personnel du signataire.",
                                    "Every attendance action requires the signer's own password."),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TeamField(t("Rechercher", "Search"), daySearch, "today-search", loading) { daySearch = it; dayOffset = 0 }
                        TeamRoles(dayRole, true, actorRole, french) { dayRole = it; dayOffset = 0 }
                        OutlinedButton(enabled = !loading, onClick = { revision++ }) { Text(t("Actualiser", "Refresh")) }
                        if (today.rows.isEmpty()) TeamEmptyState(t("Aucune personne à afficher.", "No people to display."))
                    }
                    items(today.rows.chunked(columns), key = { it.first().accountId }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { subject ->
                                TeamTodayCard(subject, french, loading, Modifier.weight(1f)) { signing = subject }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    item { TeamPager(dayOffset, 40, today.hasMore, french) { dayOffset = it } }
                }
            }
            "history" -> if (canReadPresence) {
                LazyColumn(Modifier.fillMaxSize().testTag("attendance-history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        TeamSectionHeading(t("Historique des pointages", "Attendance history"),
                            t("Dates affichées dans le fuseau de cet appareil", "Dates shown in this device's time zone"))
                        Spacer(Modifier.height(10.dp))
                        TeamField(t("Depuis (AAAA-MM-JJ)", "From (YYYY-MM-DD)"), historyFrom, "history-from", loading) { historyFrom = it; historyOffset = 0 }
                        TeamField(t("Jusqu'au (AAAA-MM-JJ)", "To (YYYY-MM-DD)"), historyTo, "history-to", loading) { historyTo = it; historyOffset = 0 }
                        if (actorRole != "employee") TeamField(t("ID du compte à filtrer (facultatif)", "Filter account ID (optional)"),
                            historySigner, "history-signer", loading, KeyboardType.Number) { historySigner = it; historyOffset = 0 }
                        Surface(shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(t("Temps cumulé sur cette page : ", "Total time on this page: ") + readableDuration(history.completedMillis),
                                Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        if (history.items.isEmpty()) TeamEmptyState(t("Aucun pointage pour cette période.", "No attendance for this period."))
                    }
                    items(history.items, key = { it.id }) { record ->
                        TeamHistoryCard(record, french, canCorrect, loading,
                            onCorrect = { correcting = record })
                    }
                    item { TeamPager(historyOffset, 40, history.hasMore, french) { historyOffset = it } }
                }
            }
        }
    }
    signing?.let { target ->
        key(target.accountId, target.nextAction) {
            var password by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { if (!loading) { signing = null; password = "" } },
                title = { Text(t("Signature personnelle", "Personal attendance proof")) },
                text = { Column {
                    Text("${target.firstName} ${target.lastName} · ${target.username}")
                    Text(if (target.nextAction == PresenceAction.ENTER) t("Entrée", "Clock in") else t("Sortie", "Clock out"))
                    PasswordField(t("Mot de passe du signataire", "Signer's password"), password, loading, french, "presence-password") { password = it }
                } },
                confirmButton = { TextButton(enabled = !loading && password.isNotEmpty(), modifier = Modifier.testTag("presence-confirm"), onClick = {
                    val proof = PresenceProof(target.accountId, password, target.nextAction ?: return@TextButton)
                    password = ""; signing = null
                    run { service.sign(proof); revision++; info = t("Pointage enregistré.", "Attendance recorded.") }
                }) { Text(t("Signer", "Sign")) } },
                dismissButton = { TextButton(enabled = !loading, onClick = { password = ""; signing = null }) { Text(t("Annuler", "Cancel")) } })
        }
    }
    correcting?.let { target ->
        key(target.id) {
            var start by remember { mutableStateOf(target.startTime) }
            var end by remember { mutableStateOf(target.endTime ?: "") }
            var reason by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { if (!loading) correcting = null },
                title = { Text(t("Corriger le pointage", "Correct attendance")) },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(t("Horodatage UTC : AAAA-MM-JJTHH:MM:SS.mmmZ", "UTC timestamp: YYYY-MM-DDTHH:MM:SS.mmmZ"))
                    TeamField(t("Entrée", "Start"), start, "correction-start", loading) { start = it }
                    TeamField(t("Sortie", "End"), end, "correction-end", loading) { end = it }
                    TeamField(t("Motif (3 caractères minimum)", "Reason (minimum 3 characters)"), reason, "correction-reason", loading) { reason = it }
                } },
                confirmButton = { TextButton(enabled = !loading && reason.length >= 3, modifier = Modifier.testTag("correction-confirm"), onClick = {
                    val correction = AttendanceCorrection(target.id, start, end, reason)
                    correcting = null
                    run { service.correct(correction); revision++; info = t("Correction enregistrée.", "Correction recorded.") }
                }) { Text(t("Enregistrer", "Save")) } },
                dismissButton = { TextButton(enabled = !loading, onClick = { correcting = null }) { Text(t("Annuler", "Cancel")) } })
        }
    }
    temporary?.let { secret ->
        AlertDialog(onDismissRequest = { temporary = null },
            title = { Text(t("Mot de passe temporaire", "Temporary password")) },
            text = { Column {
                Text(t("À transmettre au titulaire. Ce secret ne sera plus affiché après fermeture.",
                    "Give this to the account holder. It will not be shown after closing."))
                androidx.compose.foundation.text.selection.SelectionContainer { Text(secret.value, Modifier.testTag("temporary-password")) }
            } },
            confirmButton = { TextButton(onClick = { temporary = null }) { Text(t("Fermer", "Close")) } })
    }
}

/** Pure presentation helpers. No service call or attendance mutation is performed here. */
@Composable
private fun TeamSectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TeamEmptyState(message: String) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(message, Modifier.fillMaxWidth().padding(20.dp),
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TeamInitials(first: String, last: String, tag: String) {
    val initials = (first.firstOrNull()?.uppercaseChar()?.toString().orEmpty() +
        last.firstOrNull()?.uppercaseChar()?.toString().orEmpty()).ifBlank { "?" }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(48.dp).testTag(tag)) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TeamStatus(label: String, tag: String? = null,
    accent: Boolean = false, warning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        warning -> colors.errorContainer
        accent -> colors.primaryContainer
        else -> colors.surfaceVariant
    }
    val foreground = when {
        warning -> colors.onErrorContainer
        accent -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = background,
        modifier = if (tag == null) Modifier else Modifier.testTag(tag)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamEmployeeCard(employee: EmployeeSummary, french: Boolean, loading: Boolean,
    modifier: Modifier = Modifier, onDetail: () -> Unit) {
    ElevatedCard(modifier.testTag("team-user-${employee.accountId}"),
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamInitials(employee.firstName, employee.lastName, "team-avatar-${employee.accountId}")
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${employee.firstName} ${employee.lastName}".trim(),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium)
                    Text(employee.employeeCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("@${employee.username}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TeamStatus(teamRoleLabel(employee.role, french), "team-role-${employee.accountId}")
                TeamStatus(teamEmploymentLabel(employee.employment, french),
                    "team-employment-${employee.accountId}", accent = employee.employment == "ACTIVE",
                    warning = employee.employment == "SUSPENDED")
                if (!employee.active) TeamStatus(if (french) "Accès désactivé" else "Sign-in disabled",
                    "team-access-${employee.accountId}", warning = true)
            }
            HorizontalDivider()
            TextButton(onClick = onDetail, enabled = !loading,
                modifier = Modifier.align(Alignment.End)) {
                Text(if (french) "Voir la fiche" else "View profile")
            }
        }
    }
}

/** Formats a stored ISO/UTC instant for display only; malformed legacy values remain visible. */
private fun teamLocalTime(raw: String?, french: Boolean, withDate: Boolean = false): String {
    if (raw.isNullOrBlank()) return "—"
    return runCatching {
        val pattern = if (withDate) "dd MMM yyyy · HH:mm" else "HH:mm"
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val instant = input.parse(raw) ?: return@runCatching raw
        SimpleDateFormat(pattern, if (french) Locale.FRENCH else Locale.ENGLISH).apply {
            timeZone = TimeZone.getDefault()
        }.format(instant)
    }.getOrDefault(raw)
}

@Composable
private fun TeamTimeCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TeamTodayCard(subject: DailyAttendanceRow, french: Boolean, loading: Boolean,
    modifier: Modifier = Modifier, onSign: () -> Unit) {
    val time = subject.attendance
    val status = when {
        time == null -> if (french) "Non pointé" else "Not clocked in"
        time.endTime == null -> if (french) "En service" else "On duty"
        else -> if (french) "Sortie signée" else "Clocked out"
    }
    ElevatedCard(modifier.testTag("presence-user-${subject.accountId}"),
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamInitials(subject.firstName, subject.lastName, "presence-avatar-${subject.accountId}")
                Column(Modifier.weight(1f)) {
                    Text("${subject.firstName} ${subject.lastName}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(teamRoleLabel(subject.role, french), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TeamStatus(status, "presence-status-${subject.accountId}",
                accent = time != null && time.endTime == null)
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamTimeCell(if (french) "Entrée" else "Clock in",
                    teamLocalTime(time?.startTime, french), Modifier.weight(1f))
                TeamTimeCell(if (french) "Sortie" else "Clock out",
                    teamLocalTime(time?.endTime, french), Modifier.weight(1f))
            }
            Text((if (french) "Temps cumulé : " else "Total time: ") +
                readableDuration(subject.completedMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            subject.nextAction?.let { action ->
                Button(enabled = !loading,
                    modifier = Modifier.fillMaxWidth().testTag("presence-sign-${subject.accountId}"),
                    onClick = onSign) {
                    Text(if (action == PresenceAction.ENTER) {
                        if (french) "Signer l'entrée" else "Clock in"
                    } else {
                        if (french) "Signer la sortie" else "Clock out"
                    })
                }
            }
        }
    }
}

@Composable
private fun TeamHistoryCard(record: AttendanceView, french: Boolean,
    canCorrect: Boolean, loading: Boolean, onCorrect: () -> Unit) {
    var showAudit by remember(record.id) { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().testTag("attendance-${record.id}"),
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(teamLocalTime(record.startTime, french, withDate = true),
                        style = MaterialTheme.typography.titleMedium)
                    Text((if (french) "Collaborateur #" else "Team member #") + record.signerId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TeamStatus(if (record.status == "CORRECTED") {
                    if (french) "Corrigé" else "Corrected"
                } else if (record.endTime == null) {
                    if (french) "En cours" else "In progress"
                } else {
                    if (french) "Terminé" else "Completed"
                }, "attendance-status-${record.id}", accent = record.endTime == null)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamTimeCell(if (french) "Entrée" else "Clock in",
                    teamLocalTime(record.startTime, french), Modifier.weight(1f))
                TeamTimeCell(if (french) "Sortie" else "Clock out",
                    teamLocalTime(record.endTime, french), Modifier.weight(1f))
            }
            record.correctionReason?.let { reason ->
                Text((if (french) "Motif : " else "Reason: ") + reason,
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showAudit = !showAudit },
                modifier = Modifier.testTag("attendance-details-${record.id}")) {
                Text(if (showAudit) {
                    if (french) "Masquer les détails" else "Hide details"
                } else {
                    if (french) "Détails techniques" else "Technical details"
                })
            }
            if (showAudit) {
                Column(Modifier.fillMaxWidth().testTag("attendance-audit-${record.id}"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("#${record.id} · ${record.signerId} · ${record.status}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("UTC: ${record.startTime} → ${record.endTime ?: "—"}",
                        style = MaterialTheme.typography.bodySmall)
                    Text(record.source, style = MaterialTheme.typography.bodySmall)
                    if (record.originalStartTime != null || record.originalEndTime != null) {
                        Text((if (french) "Valeurs initiales : " else "Original values: ") +
                            "${record.originalStartTime ?: "—"} → ${record.originalEndTime ?: "—"}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    record.correctedBy?.let { author ->
                        Text((if (french) "Corrigé par #" else "Corrected by #") + author +
                            " · ${record.correctedAt ?: "—"}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (canCorrect) OutlinedButton(enabled = !loading, onClick = onCorrect) {
                Text(if (french) "Corriger" else "Correct")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmployeeEditor(
    service: TeamService,
    original: EmployeeDetail?,
    creating: Boolean,
    canWrite: Boolean,
    actorRole: String,
    french: Boolean,
    pickPhoto: ((SelectedImage?) -> Unit) -> Unit,
    onSaved: (EmployeeDetail, TemporaryPassword?) -> Unit,
    onBack: () -> Unit,
) {
    fun t(fr: String, en: String) = if (french) fr else en
    val editable = canWrite && (creating || actorRole == "owner" || original?.role == "employee")
    var username by remember { mutableStateOf(original?.username ?: "") }
    var first by remember { mutableStateOf(original?.firstName ?: "") }
    var last by remember { mutableStateOf(original?.lastName ?: "") }
    var email by remember { mutableStateOf(original?.email ?: "") }
    var phone by remember { mutableStateOf(original?.phone ?: "") }
    var hire by remember { mutableStateOf(original?.hireDate ?: "") }
    var role by remember { mutableStateOf(original?.role ?: "employee") }
    var code by remember { mutableStateOf(original?.employeeCode ?: "") }
    var employment by remember { mutableStateOf(original?.employment ?: "ACTIVE") }
    var address by remember { mutableStateOf(original?.address ?: "") }
    var end by remember { mutableStateOf(original?.endDate ?: "") }
    var active by remember { mutableStateOf(original?.active ?: true) }
    var manual by remember { mutableStateOf(false) }
    var secret by remember { mutableStateOf("") } // Never saved or logged.
    var selectedPhoto by remember { mutableStateOf<SelectedImage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: TeamFailure) { error = when (failure.code) {
                TeamError.CASH_OPEN -> t("Caisse ouverte : changement sensible interdit.", "Open cash: sensitive change prohibited.")
                TeamError.MEDIA_TOO_LARGE -> t("Photo supérieure à 512 Kio.", "Photo exceeds 512 KiB.")
                TeamError.FORBIDDEN -> t("Modification non autorisée.", "Modification not authorized.")
                TeamError.CONFLICT -> t("Compte, identité ou code déjà utilisé.", "Account, identity or code already used.")
                else -> t("Vérifiez les données saisies et la photo.", "Check the data and photo.")
            } }
            catch (_: SecurityFailure) { error = t("Accès refusé.", "Access denied.") }
            catch (_: Exception) { error = t("Enregistrement indisponible.", "Save unavailable.") }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().testTag("employee-editor"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        error?.let { Text(it, Modifier.testTag("employee-error"), color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            .testTag("employee-form-scroll"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (creating) t("Créer un employé", "Create employee") else t("Fiche employé", "Employee profile"),
                style = MaterialTheme.typography.headlineSmall)
            Text(t("Les informations sont regroupées par usage. Seuls les champs nécessaires sont obligatoires.",
                "Details are grouped by purpose. Only essential fields are required."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        ElevatedCard(Modifier.fillMaxWidth().testTag("employee-section-identity")) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamSectionHeading(t("Identité", "Identity"),
                    t("Identification du collaborateur et connexion", "Employee identification and sign-in"))
        TeamField(t("Identifiant de connexion", "Sign-in username"), username, "employee-username", busy || !editable) { username = it }
        TeamField(t("Prénom", "First name"), first, "employee-first", busy || !editable) { first = it }
        TeamField(t("Nom", "Last name"), last, "employee-last", busy || !editable) { last = it }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth().testTag("employee-section-contact")) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamSectionHeading(t("Coordonnées", "Contact"),
                    t("Toutes les coordonnées sont facultatives", "All contact details are optional"))
        TeamField("Email", email, "employee-email", busy || !editable, KeyboardType.Email) { email = it }
        TeamField(t("Téléphone", "Phone"), phone, "employee-phone", busy || !editable, KeyboardType.Phone) { phone = it }
        TeamField(t("Adresse", "Address"), address, "employee-address", busy || !editable) { address = it }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth().testTag("employee-section-employment")) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamSectionHeading(t("Emploi", "Employment"),
                    t("Matricule interne et situation professionnelle", "Internal reference and employment status"))
        TeamField(t("Date d'embauche (facultative)", "Hire date (optional)"), hire, "employee-hire", busy || !editable) { hire = it }
        TeamField(if (creating) t("Matricule (facultatif)", "Reference code (optional)")
            else t("Matricule employé", "Employee reference code"), code, "employee-code", busy || !editable) { code = it }
        Text(if (creating) t("Laissez vide : STORE attribuera un code unique (ex. EMP-0001). Vous pouvez aussi saisir un code interne personnalisé. Ce code ne sert pas à la connexion.",
            "Leave blank: STORE assigns a unique code (e.g. EMP-0001). You may also enter a custom internal reference. This code is not used to sign in.")
            else t("Référence interne unique ; elle ne sert pas à la connexion.",
                "Unique internal reference; it is not used to sign in."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var statusMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(enabled = editable && !busy, modifier = Modifier.testTag("employee-employment"),
                onClick = { statusMenu = true }) {
                Text(t("Situation RH : ", "Employment status: ") + teamEmploymentLabel(employment, french))
            }
            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                for (value in listOf("ACTIVE", "ABSENT", "SUSPENDED", "RESIGNED", "ARCHIVED")) {
                    DropdownMenuItem(text = { Text(teamEmploymentLabel(value, french)) }, onClick = {
                        if (editable && !busy) employment = value
                        statusMenu = false
                    })
                }
            }
        }
        if (!creating) TeamField(t("Date de départ", "End date"), end, "employee-end", busy || !editable) { end = it }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth().testTag("employee-section-access")) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamSectionHeading(t("Compte et accès", "Account and access"),
                    t("Rôle et autorisation de connexion", "Role and sign-in permissions"))
        Text(t("Rôle du compte", "Account role"), style = MaterialTheme.typography.labelLarge)
        TeamRoles(role, false, actorRole, french, editable && !busy) { role = it }
        if (!creating) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = active, onCheckedChange = { active = it }, enabled = editable && !busy,
                    modifier = Modifier.testTag("employee-active"))
                Text(t("Autoriser la connexion", "Allow sign-in"))
            }
            Text(t("L'activation du compte est distincte de la situation RH.",
                "Account access is separate from employment status."), style = MaterialTheme.typography.bodySmall)
        }
            }
        }
        if (editable) {
            ElevatedCard(Modifier.fillMaxWidth().testTag("employee-section-password")) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamSectionHeading(if (creating) t("Mot de passe initial", "Initial password")
                        else t("Gestion du mot de passe", "Password management"),
                        t("Génération sécurisée ou saisie manuelle", "Secure generation or manual entry"))
            Column {
                Row {
                    RadioButton(selected = !manual, onClick = { manual = false; secret = "" }, enabled = !busy)
                    Text(t("Générer temporaire", "Generate temporary"))
                }
                Row {
                    RadioButton(selected = manual, onClick = { manual = true; secret = "" }, enabled = !busy)
                    Text(t("Définir manuellement", "Set manually"))
                }
            }
            if (manual) PasswordField(t("Mot de passe (8 caractères minimum)", "Password (minimum 8 characters)"), secret,
                busy, french, "employee-password") { secret = it }
                }
            }
            ElevatedCard(Modifier.fillMaxWidth().testTag("employee-section-photo")) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamSectionHeading(t("Photo de profil (facultative)", "Profile photo (optional)"),
                        t("Ajouter maintenant ou plus tard", "Add now or later"))
            Text(t("L'employé peut être créé sans photo. JPEG, PNG ou WebP ; 512 Kio maximum.",
                "The employee can be created without a photo. JPEG, PNG or WebP; 512 KiB maximum."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (original?.hasPhoto == true) TeamProfileImage(service, original.accountId, revision)
            else if (selectedPhoto == null) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(64.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(((first.firstOrNull()?.uppercaseChar()?.toString() ?: "") +
                            (last.firstOrNull()?.uppercaseChar()?.toString() ?: "")).ifBlank { "?" },
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Text(if (selectedPhoto != null) t("Nouvelle photo sélectionnée", "New photo selected") else t("Aucune nouvelle photo", "No new photo"))
            OutlinedButton(enabled = !busy, onClick = { pickPhoto { selected -> if (selected != null) selectedPhoto = selected } }) {
                Text(t("Choisir une photo", "Choose photo"))
            }
            if (original != null && selectedPhoto != null) Button(enabled = !busy, onClick = { run {
                val updated = service.photo(original.accountId, ProfilePhotoChange(replacement = selectedPhoto))
                selectedPhoto = null; revision++; onSaved(updated, null)
            } }) { Text(t("Appliquer la photo", "Apply photo")) }
            if (original != null && original.hasPhoto) OutlinedButton(enabled = !busy, onClick = { run {
                val updated = service.photo(original.accountId, ProfilePhotoChange(remove = true))
                selectedPhoto = null; revision++; onSaved(updated, null)
            } }) { Text(t("Supprimer la photo", "Remove photo")) }
                }
            }
        } else if (original?.hasPhoto == true) TeamProfileImage(service, original.accountId, revision)
        }
        // Primary actions remain reachable while the form scrolls or the IME is open.
        if (editable) {
            Surface(Modifier.fillMaxWidth().testTag("employee-section-actions"),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (creating) Button(modifier = Modifier.fillMaxWidth().testTag("employee-save"),
                enabled = !busy && username.isNotBlank() && first.isNotBlank() && last.isNotBlank() && (!manual || secret.length >= 8),
                onClick = { run {
                val written = service.create(EmployeeCreation(username, first, last, email.ifBlank { null }, phone.ifBlank { null },
                    hire.ifBlank { null }, role, code, employment, address.ifBlank { null }, if (manual) secret else null,
                    !manual, selectedPhoto))
                secret = ""; selectedPhoto = null; onSaved(written.employee, written.temporaryPassword)
            } }) { Text(t("Créer l'employé", "Create employee")) }
            if (!creating && original != null) {
                Button(modifier = Modifier.fillMaxWidth().testTag("employee-save"), enabled = !busy, onClick = { run {
                    val updated = service.update(EmployeeUpdate(original.accountId, username, first, last,
                        email.ifBlank { null }, phone.ifBlank { null }, hire.ifBlank { null }, active, role,
                        code, employment, address.ifBlank { null }, end.ifBlank { null }))
                    secret = ""; onSaved(updated, null)
                } }) { Text(t("Enregistrer la fiche", "Save profile")) }
                OutlinedButton(enabled = !busy && (!manual || secret.length >= 8), onClick = { run {
                    val temporary = service.password(PasswordChange(original.accountId, if (manual) secret else null, !manual))
                    secret = ""; onSaved(original, temporary)
                } }) { Text(t("Changer le mot de passe", "Change password")) }
            }
                }
            }
        }
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("employee-cancel"),
            onClick = { secret = ""; selectedPhoto = null; onBack() }) {
            Text(if (creating) t("Annuler", "Cancel") else t("Fermer la fiche", "Close profile"))
        }
    }

}

@Composable
private fun TeamProfileImage(service: TeamService, accountId: Long, revision: Int) {
    var bitmap by remember(accountId, revision) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(service, accountId, revision) {
        try {
            val photo = service.photo(accountId)
            bitmap = withContext(Dispatchers.Default) { photo?.bytes?.let { bytes ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) null
                else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { bitmap = null }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(96.dp).testTag("employee-photo"), contentScale = ContentScale.Crop) }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TeamField(label: String, value: String, tag: String, disabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text, changed: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // On short screens the IME can leave no height for the pinned editor actions.
    // Done closes the IME explicitly without submitting any business mutation.
    OutlinedTextField(value, changed, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).testTag(tag),
        enabled = !disabled, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamRoles(selected: String, includeAll: Boolean, actorRole: String, french: Boolean,
    enabled: Boolean = true, changed: (String) -> Unit) {
    val values = (if (includeAll) listOf("") else emptyList()) +
        (if (includeAll) listOf("owner", "manager", "employee") else if (actorRole == "owner") listOf("manager", "employee") else listOf("employee"))
    if (includeAll) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (value in values) FilterChip(selected == value, onClick = { changed(value) },
                label = { Text(if (value.isEmpty()) if (french) "Tous" else "All" else teamRoleLabel(value, french)) })
        }
    } else {
        var roleMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { roleMenu = true }, enabled = enabled,
                modifier = Modifier.testTag("employee-role")) {
                Text(teamRoleLabel(selected, french))
            }
            DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                for (value in values) DropdownMenuItem(text = { Text(teamRoleLabel(value, french)) },
                    onClick = { changed(value); roleMenu = false })
            }
        }
    }
}

@Composable
private fun TeamPager(offset: Int, limit: Int, more: Boolean, french: Boolean, changed: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = offset > 0, onClick = { changed(maxOf(0, offset - limit)) }) { Text(if (french) "Précédent" else "Previous") }
        OutlinedButton(enabled = more, onClick = { changed(offset + limit) }) { Text(if (french) "Suivant" else "Next") }
    }
}

private fun readableDuration(value: Long): String {
    val minutes = value.coerceAtLeast(0L) / 60_000L
    return "${minutes / 60} h ${minutes % 60} min"
}

private fun teamRoleLabel(role: String, french: Boolean): String = when (role) {
    "owner" -> if (french) "Propriétaire" else "Owner"
    "manager" -> if (french) "Responsable" else "Manager"
    "employee" -> if (french) "Employé" else "Employee"
    else -> role
}

private fun teamEmploymentLabel(status: String, french: Boolean): String = when (status) {
    "ACTIVE" -> if (french) "En activité" else "Active"
    "ABSENT" -> if (french) "Absent" else "Absent"
    "SUSPENDED" -> if (french) "Suspendu" else "Suspended"
    "RESIGNED" -> if (french) "Parti" else "Resigned"
    "ARCHIVED" -> if (french) "Archivé" else "Archived"
    else -> status
}
