package com.example.ui.features.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ParentLedgerSummary
import com.example.core.Result
import com.example.domain.model.Student
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.StudentRepository
import com.example.ui.components.ElCard
import com.example.ui.components.ElInfoRow
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val _student = MutableStateFlow<Student?>(null)
    val student: StateFlow<Student?> = _student.asStateFlow()

    private val _siblings = MutableStateFlow<List<Student>>(emptyList())
    val siblings: StateFlow<List<Student>> = _siblings.asStateFlow()

    private val _familySummary = MutableStateFlow<ParentLedgerSummary?>(null)
    val familySummary: StateFlow<ParentLedgerSummary?> = _familySummary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(studentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            studentRepository.observeById(studentId).collect { s ->
                _student.value = s
                if (s != null) {
                    studentRepository.observeByParent(s.parentId).collect { sibs ->
                        _siblings.value = sibs.filter { it.id != studentId }
                    }
                    when (val result = ledgerRepository.summary(s.parentId)) {
                        is Result.Ok -> _familySummary.value = result.value
                        is Result.Err -> _error.value = result.error.userMessage
                    }
                }
                _isLoading.value = false
            }
        }
    }
}

@Composable
fun StudentDetailScreen(
    studentId: String,
    onBack: () -> Unit,
    viewModel: StudentDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(studentId) { viewModel.load(studentId) }
    val student by viewModel.student.collectAsState()
    val siblings by viewModel.siblings.collectAsState()
    val familySummary by viewModel.familySummary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    ElScaffold(
        topBar = { ElTopBar(title = student?.fullName ?: "Élève", onBack = onBack) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isLoading) Text("Chargement...")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            student?.let { s ->
                ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        ElSectionHeader(title = "Identité")
                        Spacer(Modifier.height(8.dp))
                        ElInfoRow(label = "Code", value = s.code)
                        ElInfoRow(label = "Date de naissance", value = s.birthDate)
                        ElInfoRow(label = "Niveau", value = "${s.gradeLevel} (${s.level})")
                        ElInfoRow(label = "Statut", value = s.status, valueColor = if (s.status == "active") SuccessGreen else DangerRed)
                        s.medicalNotes?.let { ElInfoRow(label = "Notes médicales", value = it) }
                    }
                }

                familySummary?.let { summary ->
                    ElCard(modifier = Modifier.fillMaxWidth(), accent = SuccessGreen) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            ElSectionHeader(title = "Finances familiales")
                            Spacer(Modifier.height(8.dp))
                            ElInfoRow(label = "Total dû", value = "${(summary.totalCharged / 100).formatDzd()} DZD")
                            ElInfoRow(label = "Total payé", value = "${(summary.totalPaid / 100).formatDzd()} DZD", valueColor = SuccessGreen)
                            ElInfoRow(label = "Solde restant", value = "${(summary.totalOutstanding / 100).formatDzd()} DZD")
                            if (summary.totalOverdue > 0) {
                                ElInfoRow(label = "En retard", value = "${(summary.totalOverdue / 100).formatDzd()} DZD", valueColor = DangerRed)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Comptes (${summary.accounts.size}):", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                            summary.accounts.forEach { acc ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(acc.category.name, style = MaterialTheme.typography.bodySmall)
                                    Text("${(acc.balance / 100).formatDzd()} DZD", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                if (siblings.isNotEmpty()) {
                    ElCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            ElSectionHeader(title = "Fratrie (${siblings.size})")
                            Spacer(Modifier.height(8.dp))
                            siblings.forEach { sib ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("• ${sib.fullName}", style = MaterialTheme.typography.bodyMedium)
                                    Text(sib.gradeLevel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun Long.formatDzd(): String = "%,.0f".format(this.toDouble())
