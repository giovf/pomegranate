package com.gankdat.pomegranate.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.components.MediaStateIndicator
import com.gankdat.pomegranate.ui.permissions.PrivacyPromises
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4
private const val PAGE_DESTINATION = 3

@Composable
internal fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(
        factory = appViewModelFactory { OnboardingViewModel(it.settings, ResolverDestinationAccess(it.contentResolver)) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onDestinationPicked(uri)
    }

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> PurposePage()
                1 -> PrivacyPage()
                2 -> ThreeStatesPage()
                else -> DestinationPage(state = state, onChooseFolder = { picker.launch(null) })
            }
        }
        PageIndicator(current = pagerState.currentPage)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Text(stringResource(R.string.onboarding_back))
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            if (pagerState.currentPage < PAGE_DESTINATION) {
                Button(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Text(stringResource(R.string.onboarding_next))
                }
            } else {
                Button(enabled = state.isDestinationSelected, onClick = { viewModel.completeOnboarding(onFinished) }) {
                    Text(stringResource(R.string.onboarding_get_started))
                }
            }
        }
    }

    if (state.showBackupWarning) {
        AlertDialog(
            onDismissRequest = viewModel::acknowledgeBackupWarning,
            title = { Text(stringResource(R.string.backup_warning_title)) },
            text = { Text(stringResource(R.string.backup_warning_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.acknowledgeBackupWarning(); picker.launch(null) }) { Text(stringResource(R.string.backup_warning_change)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::acknowledgeBackupWarning) { Text(stringResource(R.string.backup_warning_keep)) }
            },
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.destination_error_title)) },
            text = {
                Text(
                    stringResource(
                        when (error) {
                            OnboardingError.PERMISSION_NOT_PERSISTABLE -> R.string.destination_error_not_persistable
                            OnboardingError.DESTINATION_REVOKED -> R.string.destination_error_revoked
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun Page(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun PurposePage() {
    Page(stringResource(R.string.onboarding_purpose_title)) {
        Text(stringResource(R.string.onboarding_purpose_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PrivacyPage() {
    Page(stringResource(R.string.onboarding_privacy_title)) {
        Text(stringResource(R.string.onboarding_privacy_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        PrivacyPromises()
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.privacy_promise_tokens_wiped), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ThreeStatesPage() {
    Page(stringResource(R.string.onboarding_states_title)) {
        Text(stringResource(R.string.onboarding_states_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        MediaState.entries.forEach { state ->
            MediaStateIndicator(state = state, showLabel = true, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun DestinationPage(state: OnboardingUiState, onChooseFolder: () -> Unit) {
    Page(stringResource(R.string.onboarding_destination_title)) {
        Text(stringResource(R.string.onboarding_destination_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                Icon(painter = painterResource(R.drawable.ic_folder), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val destination = state.destination
                    if (destination != null) {
                        Text(
                            text = destination.label(stringResource(R.string.storage_internal), stringResource(R.string.storage_removable)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (destination.isRemovable) {
                            Text(stringResource(R.string.destination_removable_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(stringResource(R.string.destination_none), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.isDestinationSelected) R.string.destination_change else R.string.destination_choose))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.destination_sd_guidance), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PageIndicator(current: Int) {
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, PAGE_COUNT)
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { contentDescription = description },
    ) {
        repeat(PAGE_COUNT) { index ->
            val color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
