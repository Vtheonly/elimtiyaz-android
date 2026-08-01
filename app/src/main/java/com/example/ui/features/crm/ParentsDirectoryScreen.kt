package com.example.ui.features.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.Parent
import com.example.domain.repository.ParentRepository
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElTextField
import com.example.ui.theme.elDesignTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        ElTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = "Rechercher un parent",
            placeholder = "Nom, téléphone, code...",
            leadingIcon = Icons.Default.Person,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        error?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }

        if (parents.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Person,
                title = "Aucun parent trouvé",
                message = "Essayez de modifier votre recherche ou ajoutez un nouveau parent.",
                modifier = Modifier.padding(top = 32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(parents) { parent ->
                    ParentCard(
                        parent = parent,
                        onClick = { onParentClick(parent.id) },
                        onCall = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                data = android.net.Uri.parse("tel:${parent.phone}")
                            }
                            context.startActivity(intent)
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
) {
    val tokens = elDesignTokens()
    ElCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        compact = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElAvatar(initials = parent.fullName, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(parent.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(parent.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(parent.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tokens.successBrush)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCall,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Call, contentDescription = "Appeler ${parent.fullName}", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
