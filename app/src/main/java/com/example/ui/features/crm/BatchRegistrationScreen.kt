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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BatchRegistrationViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _activationCode = MutableStateFlow<String?>(null)
    val activationCode: StateFlow<String?> = _activationCode.asStateFlow()

    fun register(parent: CreateParentInput, students: List<CreateStudentInput>, onSuccess: () -> Unit) {
        if (parent.firstName.isBlank() || parent.lastName.isBlank() || parent.phone.isBlank()) {
            _error.value = "Veuillez renseigner le prénom, nom et téléphone du parent"
            return
        }
        if (students.isEmpty()) {
            _error.value = "Au moins un élève est requis"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = studentRepository.batchRegister(parent, students, actorId, actorName)) {
                is Result.Ok -> {
                    _isLoading.value = false
                    _activationCode.value = result.value.activationCode
                    onSuccess()
                }
                is Result.Err -> {
                    _isLoading.value = false
                    _error.value = result.error.userMessage
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRegistrationScreen(
    onSuccess: () -> Unit,
    viewModel: BatchRegistrationViewModel = hiltViewModel(),
) {
    var parentFirstName by remember { mutableStateOf("") }
    var parentLastName by remember { mutableStateOf("") }
    var parentPhone by remember { mutableStateOf("") }
    var parentEmail by remember { mutableStateOf("") }
    var parentOccupation by remember { mutableStateOf("") }
    var parentAddress by remember { mutableStateOf("") }

    val children = remember { mutableStateListOf(ChildFormState()) }
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val activationCode by viewModel.activationCode.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Inscription famille") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { children.add(ChildFormState()) }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un enfant")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Parent form
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Parent / Tuteur", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = parentFirstName, onValueChange = { parentFirstName = it }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = parentLastName, onValueChange = { parentLastName = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = parentPhone, onValueChange = { parentPhone = it }, label = { Text("Téléphone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = parentEmail, onValueChange = { parentEmail = it }, label = { Text("Email (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = parentOccupation, onValueChange = { parentOccupation = it }, label = { Text("Profession (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = parentAddress, onValueChange = { parentAddress = it }, label = { Text("Adresse (optionnel)") }, modifier = Modifier.fillMaxWidth())
                }
            }

            // Children forms
            children.forEachIndexed { index, child ->
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Enfant ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (children.size > 1) {
                                IconButton(onClick = { children.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = child.firstName, onValueChange = { children[index] = child.copy(firstName = it) }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = child.lastName, onValueChange = { children[index] = child.copy(lastName = it) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = child.birthDate, onValueChange = { children[index] = child.copy(birthDate = it) }, label = { Text("Date de naissance (AAAA-MM-JJ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = child.gradeLevel, onValueChange = { children[index] = child.copy(gradeLevel = it) }, label = { Text("Niveau (ex: 1AP, 2AM, 1ere_annee)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            activationCode?.let { code ->
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Inscription réussie!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Code d'activation: $code")
                        Text("Donnez ce code au parent pour qu'il puisse se connecter au portail web.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                onClick = {
                    val parent = CreateParentInput(
                        firstName = parentFirstName, lastName = parentLastName, phone = parentPhone,
                        email = parentEmail.ifBlank { null }, occupation = parentOccupation.ifBlank { null },
                        address = parentAddress.ifBlank { null },
                    )
                    val students = children.map { c ->
                        CreateStudentInput(
                            firstName = c.firstName, lastName = c.lastName,
                            gender = "unspecified", birthDate = c.birthDate,
                            level = c.gradeLevel.substringBefore("_").substringBefore("ere").let {
                                when { it.contains("ap") -> "primaire"; it.contains("am") -> "cem"; it.contains("nnee") -> "lycee"; else -> "primaire" }
                            },
                            gradeLevel = c.gradeLevel,
                        )
                    }
                    viewModel.register(parent, students, onSuccess)
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (isLoading) "Inscription..." else "Inscrire la famille")
            }
        }
    }
}

data class ChildFormState(
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val gradeLevel: String = "",
)
