package com.example.ui.features.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LedgerEngine
import com.example.core.ParentLedgerSummary
import com.example.core.Result
import com.example.domain.model.Student
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.StudentRepository
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
            // Observe the student + siblings
            studentRepository.observeById(studentId).collect { s ->
                _student.value = s
                if (s != null) {
                    // Fetch siblings
                    studentRepository.observeByParent(s.parentId).collect { sibs ->
                        _siblings.value = sibs.filter { it.id != studentId }
                    }
                    // Fetch family ledger summary
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(student?.fullName ?: "Élève") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoading) Text("Chargement...")

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            student?.let { s ->
                // Identity card
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Identité", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Code: ${s.code}")
                        Text("Date de naissance: ${s.birthDate}")
                        Text("Niveau: ${s.gradeLevel} (${s.level})")
                        Text("Statut: ${s.status}")
                        s.medicalNotes?.let { Text("Notes médicales: $it") }
                    }
                }

                // Family financial summary
                familySummary?.let { summary ->
                    Card(elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Finances familiales", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("Total dû: ${(summary.totalCharged / 100).formatDzd()}")
                            Text("Total payé: ${(summary.totalPaid / 100).formatDzd()}")
                            Text("Solde restant: ${(summary.totalOutstanding / 100).formatDzd()}")
                            if (summary.totalOverdue > 0) {
                                Text(
                                    "En retard: ${(summary.totalOverdue / 100).formatDzd()}",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Comptes (${summary.accounts.size}):", style = MaterialTheme.typography.labelMedium)
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

                // Siblings
                if (siblings.isNotEmpty()) {
                    Card(elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Fratrie (${siblings.size})", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            siblings.forEach { sib ->
                                Text("• ${sib.fullName} (${sib.gradeLevel})", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun Long.formatDzd(): String = "%,.0f".format(this.toDouble())
