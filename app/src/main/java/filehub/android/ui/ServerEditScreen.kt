package filehub.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import filehub.android.R
import filehub.android.data.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    session: SessionState,
    profileId: String,
    onSave: (displayName: String, addresses: List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val profile = session.profiles.firstOrNull { it.id == profileId }
    var displayName by rememberSaveable { mutableStateOf(profile?.displayName.orEmpty()) }
    var addresses by remember(profile?.id) {
        mutableStateOf(
            profile?.addresses?.ifEmpty { listOf(profile.baseUrl) }?.toMutableList()
                ?: mutableListOf(),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.server_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Text(
                text = stringResource(R.string.server_addresses),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.server_addresses_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            addresses.forEachIndexed { index, address ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { value ->
                            addresses = addresses.toMutableList().also { it[index] = value }
                        },
                        label = { Text(stringResource(R.string.server_address)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    IconButton(
                        onClick = {
                            addresses = addresses.toMutableList().also { it.removeAt(index) }
                        },
                        enabled = addresses.size > 1,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    }
                }
            }
            OutlinedButton(
                onClick = { addresses = (addresses + "").toMutableList() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.server_add_address))
            }
            Button(
                onClick = { onSave(displayName, addresses.filter { it.isNotBlank() }) },
                enabled = addresses.any { it.isNotBlank() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.server_save))
            }
        }
    }
}
