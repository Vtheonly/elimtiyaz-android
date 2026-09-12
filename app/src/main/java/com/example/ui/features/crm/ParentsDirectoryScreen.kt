package com.example.ui.features.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.Parent
import com.example.domain.repository.ParentRepository
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.input.ElSearchBar
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.util.PhoneUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * T-320 (55th session, UI-310) — CRM parents directory, migrated to the
 * canonical design system with a context metric strip, a real search bar
 * (built-in clear), a relationship tag, and a WhatsApp quick-action beside
 * the call action. The ViewModel contract is unchanged.
 */
@HiltViewModel
class ParentsDirectoryViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val parents: StateFlow<List<Parent>> = _query
        .flatMapLatest { q -> parentRepository.search(q) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setQuery(q: String) { _query.value = q }

    fun deleteParent(parent: Parent, actorId: String, actorName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = parentRepository.deleteParent(parent.id, actorId, actorName)) {
                is Result.Ok -> { _isLoading.value = false }
                is Result.Err -> { _isLoading.value = false; _error.value = result.error.userMessage }
            }
        }
    }
}

@Composable
fun ParentsDirectoryScreen(
    session: Session,
    onParentClick: (String) -> Unit,
    viewModel: ParentsDirectoryViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val parents by viewModel.parents.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Context strip — the count lives here instead of the search label
        // (ElSearchBar has no label slot).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.FamilyRestroom,
                contentDescription = null,
                tint = ElTheme.colors.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                "${parents.size} ${if (parents.size > 1) "familles enregistrées" else "famille enregistrée"}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ElTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        ElSearchBar(
            query = query,
            onQueryChange = viewModel::setQuery,
            placeholder = "Nom, prénom, téléphone, code…",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
        )

        error?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }

        if (parents.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Person,
                title = "Aucun parent trouvé",
                subtitle = if (query.isBlank()) "Aucun parent enregistré." else "Aucun parent ne correspond à « $query ».",
                modifier = Modifier.padding(top = 32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(parents, key = { it.id }) { parent ->
                    ParentCard(
                        parent = parent,
                        onClick = { onParentClick(parent.id) },
                        onCall = { PhoneUtils.dial(context, parent.phone) },
                        onWhatsApp = {
                            // whatsapp column falls back to the main phone at
                            // registration time (batchRegister), so try it first.
                            PhoneUtils.openWhatsApp(context, parent.whatsapp ?: parent.phone)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentCard(
    parent: Parent,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
) {
    val c = ElTheme.colors
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        size = ElCardSize.COMPACT,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElAvatar(initials = parent.fullName, size = ElAvatarSize.M)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        parent.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    parent.relationship?.let { relationship ->
                        ElTag(text = relationship.replaceFirstChar { it.uppercase() }, tone = ElTagTone.INFO)
                    }
                }
                Text(
                    "Matricule : ${parent.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                Text(
                    "Tél : ${parent.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            QuickActionCircle(
                icon = Icons.Default.Call,
                contentDescription = "Appeler ${parent.fullName}",
                background = c.successContainer,
                tint = c.success,
                onClick = onCall,
            )
            Spacer(Modifier.width(6.dp))
            QuickActionCircle(
                icon = Icons.Default.Forum,
                contentDescription = "Message WhatsApp à ${parent.fullName}",
                background = c.primaryContainer,
                tint = c.primary,
                onClick = onWhatsApp,
            )
        }
    }
}

@Composable
private fun QuickActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    background: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .pressClickable(onClick = onClick)
            .background(background, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(19.dp))
    }
}
