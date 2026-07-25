package com.elimtiyaz.feature.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.domain.model.ExpenseCategory

/** Submit a new expense request — used by any user with [Permission.SubmitExpense]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSubmitScreen(
    nav: NavController,
    vm: ExpenseViewModel = hiltViewModel(),
) {
    val state by vm.submitState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle dépense", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(ElimtiyazSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = vm::titleChanged,
                label = { Text("Titre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = vm::descriptionChanged,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
            OutlinedTextField(
                value = state.amount,
                onValueChange = vm::amountChanged,
                label = { Text("Montant (DZD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Category dropdown
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = labelForExpenseCategory(state.category),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Catégorie") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                )
                androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ExpenseCategory.values().forEach { c ->
                        DropdownMenuItem(text = { Text(labelForExpenseCategory(c)) }, onClick = { vm.categoryChanged(c); expanded = false })
                    }
                }
            }

            OutlinedTextField(
                value = state.payee,
                onValueChange = vm::payeeChanged,
                label = { Text("Bénéficiaire") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { vm.submit(onSuccess = { _, _ -> nav.popBackStack() }) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = state.canSubmit && !state.isSubmitting,
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = ElimtiyazSpacing.x2).height(20.dp),
                    )
                    Text("Envoi…")
                } else {
                    Text("Soumettre", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
        }
    }
}
