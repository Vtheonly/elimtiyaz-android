package com.example.ui.features.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElFab
import com.example.ui.components.ElIconButton
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTextField
import com.example.ui.components.ElTopBar
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen

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

    ElScaffold(
        topBar = { ElTopBar(title = "Inscription famille") },
        floatingActionButton = {
            ElFab(
                icon = Icons.Default.Add,
                onClick = { children.add(ChildFormState()) },
                contentDescription = "Ajouter un enfant",
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElSectionHeader(title = "Parent / Tuteur")
                    ElTextField(value = parentFirstName, onValueChange = { parentFirstName = it }, label = "Prénom", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentLastName, onValueChange = { parentLastName = it }, label = "Nom", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentPhone, onValueChange = { parentPhone = it }, label = "Téléphone", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentEmail, onValueChange = { parentEmail = it }, label = "Email (optionnel)", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentOccupation, onValueChange = { parentOccupation = it }, label = "Profession (optionnel)", modifier = Modifier.fillMaxWidth())
                    ElTextField(value = parentAddress, onValueChange = { parentAddress = it }, label = "Adresse (optionnel)", modifier = Modifier.fillMaxWidth(), singleLine = false)
                }
            }

            children.forEachIndexed { index, child ->
                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Enfant ${index + 1}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), modifier = Modifier.weight(1f))
                            if (children.size > 1) {
                                ElIconButton(
                                    icon = Icons.Default.Delete,
                                    onClick = { children.removeAt(index) },
                                    contentDescription = "Supprimer",
                                    size = 36,
                                    background = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        ElTextField(value = child.firstName, onValueChange = { children[index] = child.copy(firstName = it) }, label = "Prénom", modifier = Modifier.fillMaxWidth())
                        ElTextField(value = child.lastName, onValueChange = { children[index] = child.copy(lastName = it) }, label = "Nom", modifier = Modifier.fillMaxWidth())
                        ElTextField(value = child.birthDate, onValueChange = { children[index] = child.copy(birthDate = it) }, label = "Date de naissance (AAAA-MM-JJ)", modifier = Modifier.fillMaxWidth())
                        ElTextField(value = child.gradeLevel, onValueChange = { children[index] = child.copy(gradeLevel = it) }, label = "Niveau (ex: 1AP, 2AM, 1ere_annee)", modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            activationCode?.let { code ->
                ElCard(modifier = Modifier.fillMaxWidth(), accent = SuccessGreen) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Inscription réussie!", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                        Text("Code d'activation: $code", style = MaterialTheme.typography.bodyMedium)
                        Text("Donnez ce code au parent pour qu'il puisse se connecter au portail web.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            ElButton(
                text = if (isLoading) "Inscription..." else "Inscrire la famille",
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
                fullWidth = true,
                loading = isLoading,
            )
        }
    }
}
