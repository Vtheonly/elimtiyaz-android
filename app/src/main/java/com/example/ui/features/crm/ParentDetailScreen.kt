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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ParentLedgerSummary
import com.example.core.Result
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ParentDetailViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val _parent = MutableStateFlow<Parent?>(null)
    val parent: StateFlow<Parent?> = _parent.asStateFlow()

    private val _children = MutableStateFlow<List<Student>>(emptyList())
    val children: StateFlow<List<Student>> = _children.asStateFlow()

    private val _summary = MutableStateFlow<ParentLedgerSummary?>(null)
    val summary: StateFlow<ParentLedgerSummary?> = _summary.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(parentId: String) {
        viewModelScope.launch {
            parentRepository.observeById(parentId).collect { p ->
                _parent.value = p
                if (p != null) {
                    when (val result = ledgerRepository.summary(parentId)) {
                        is Result.Ok -> _summary.value = result.value
                        is Result.Err -> _error.value = result.error.userMessage
                    }
                }
            }
        }
        viewModelScope.launch {
            studentRepository.observeByParent(parentId).collect { kids ->
                _children.value = kids
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDetailScreen(
    parentId: String,
    onBack: () -> Unit,
    viewModel: ParentDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(parentId) { viewModel.load(parentId) }
    val parent by viewModel.parent.collectAsState()
    val children by viewModel.children.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(parent?.fullName ?: "Parent") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            parent?.let { p ->
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Contact", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Code: ${p.code}")
                        Text("Téléphone: ${p.phone}")
                        p.email?.let { Text("Email: $it") }
                        p.address?.let { Text("Adresse: $it") }
                        p.occupation?.let { Text("Profession: $it") }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                    data = android.net.Uri.parse("tel:${p.phone}")
                                }
                                context.startActivity(intent)
                            }) { Text("Appeler") }

                            Button(onClick = {
                                val cleanPhone = (p.whatsapp ?: p.phone).replace("[^0-9]".toRegex(), "")
                                val formatted = if (cleanPhone.startsWith("0")) "213${cleanPhone.substring(1)}" else cleanPhone
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("https://wa.me/$formatted")
                                }
                                context.startActivity(intent)
                            }) { Text("WhatsApp") }
                        }
                    }
                }
            }

            summary?.let { s ->
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Finances", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Total facturé: ${(s.totalCharged / 100).formatDzd()} DZD")
                        Text("Total payé: ${(s.totalPaid / 100).formatDzd()} DZD")
                        Text("Solde: ${(s.totalOutstanding / 100).formatDzd()} DZD")
                        if (s.totalOverdue > 0) {
                            Text("En retard: ${(s.totalOverdue / 100).formatDzd()} DZD", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Enfants (${children.size})", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    children.forEach { kid ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("• ${kid.fullName}")
                            Text(kid.gradeLevel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
