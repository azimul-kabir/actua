package com.azimulkabir.actua.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.importing.CsvTransactionCandidateSource
import com.azimulkabir.actua.data.importing.ImportCandidate
import com.azimulkabir.actua.data.importing.ImportDuplicateDetector
import com.azimulkabir.actua.data.importing.ImportProblem
import com.azimulkabir.actua.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate

private data class ReviewRow(
    val sourceRow: Int,
    val date: String,
    val payee: String,
    val amount: String,
    val notes: String,
    val reference: String?,
    val selected: Boolean,
)

@Composable
fun ImportTransactionsScreen(
    accounts: List<Account>,
    duplicateKeys: (String) -> Set<String>,
    onImport: (String, List<ImportCandidate>) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<ReviewRow>() }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var problems by remember { mutableStateOf<List<ImportProblem>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var accountMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val existingKeys = remember(account?.id, rows.size) { account?.id?.let(duplicateKeys).orEmpty() }

    fun load(uri: Uri) {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read the selected file")
            } }.map(CsvTransactionCandidateSource::parse)
                .onSuccess { result ->
                    rows.clear()
                    val knownKeys = account?.id?.let(duplicateKeys).orEmpty().toMutableSet()
                    rows += result.candidates.map { candidate ->
                        val key = ImportDuplicateDetector.key(candidate.date, candidate.amountCents, candidate.payee)
                        val duplicate = !knownKeys.add(key)
                        ReviewRow(candidate.sourceRow, formatDate(candidate.date), candidate.payee,
                            BigDecimal.valueOf(candidate.amountCents, 2).toPlainString(), candidate.notes,
                            candidate.reference, selected = !duplicate)
                    }
                    problems = result.problems
                    message = if (rows.isEmpty()) "No valid transactions found." else null
                }.onFailure { message = it.message ?: "Could not parse this CSV file." }
            busy = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(::load)
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Import transactions", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("CSV files stay on this device. Every valid row is shown for review before anything is saved.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                OutlinedButton(onClick = { accountMenu = true }, enabled = accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(account?.name ?: "No open account")
                }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    accounts.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = {
                        account = option; accountMenu = false
                    }) }
                }
            }
            Button(onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                enabled = !busy && account != null, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(if (busy) "Reading…" else "Choose CSV file")
            }
            if (problems.isNotEmpty()) Text(
                "${problems.size} malformed row${if (problems.size == 1) " was" else "s were"} excluded. " +
                    problems.take(3).joinToString { "Row ${it.sourceRow}: ${it.message}" },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            message?.let { Text(it, modifier = Modifier.padding(bottom = 8.dp)) }
            rows.forEachIndexed { index, row ->
                val candidate = row.toCandidateOrNull()
                val invalid = candidate == null
                val candidateKey = candidate?.let { ImportDuplicateDetector.key(it.date, it.amountCents, it.payee) }
                val duplicate = candidateKey != null && (candidateKey in existingKeys || rows.take(index).any {
                    it.toCandidateOrNull()?.let { prior ->
                        ImportDuplicateDetector.key(prior.date, prior.amountCents, prior.payee) == candidateKey
                    } == true
                })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = row.selected, onCheckedChange = { rows[index] = row.copy(selected = it) })
                    Text("CSV row ${row.sourceRow}", style = MaterialTheme.typography.labelLarge)
                    if (duplicate) Text("  Possible duplicate", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium)
                }
                OutlinedTextField(row.date, { rows[index] = row.copy(date = it) }, label = { Text("Date (YYYY-MM-DD)") },
                    isError = invalid, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.payee, { rows[index] = row.copy(payee = it) }, label = { Text("Payee") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.amount, { rows[index] = row.copy(amount = it) }, label = { Text("Signed amount") },
                    isError = invalid, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.notes, { rows[index] = row.copy(notes = it) }, label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { rows.removeAt(index) }) { Text("Reject") }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
        val selected = rows.filter(ReviewRow::selected)
        val ready = selected.mapNotNull(ReviewRow::toCandidateOrNull)
        Button(
            onClick = {
                val target = account ?: return@Button
                if (onImport(target.id, ready)) {
                    message = "Imported ${ready.size} transaction${if (ready.size == 1) "" else "s"}."
                    rows.clear(); problems = emptyList()
                }
            },
            enabled = selected.isNotEmpty() && ready.size == selected.size,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Approve and import ${ready.size}") }
    }
}

private fun ReviewRow.toCandidateOrNull(): ImportCandidate? = runCatching {
    val parsedDate = LocalDate.parse(date.trim())
    val cents = BigDecimal(amount.trim()).setScale(2).movePointRight(2).longValueExact()
    require(payee.isNotBlank() && cents != 0L)
    ImportCandidate(sourceRow, parsedDate.year * 10_000 + parsedDate.monthValue * 100 + parsedDate.dayOfMonth,
        payee.trim(), notes.trim(), cents, reference)
}.getOrNull()

private fun formatDate(value: Int): String = "%04d-%02d-%02d".format(value / 10_000, value / 100 % 100, value % 100)
