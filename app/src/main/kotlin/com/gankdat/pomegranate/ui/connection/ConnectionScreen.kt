package com.gankdat.pomegranate.ui.connection

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.auth.AuthRepository
import com.gankdat.pomegranate.auth.AuthState
import com.gankdat.pomegranate.ui.appViewModelFactory
import kotlinx.coroutines.launch

internal class ConnectionViewModel(private val auth: AuthRepository) : ViewModel() {
    val state = auth.authState

    fun signIn(activity: android.app.Activity) = auth.launchSignIn(activity)

    fun signOut() {
        viewModelScope.launch { auth.revokeAndClear() }
    }

    fun dismissError() = auth.dismissError()
}

/** Google account connection status with sign-in and sign-out. */
@Composable
internal fun ConnectionScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionViewModel = viewModel(factory = appViewModelFactory { ConnectionViewModel(it.auth) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.connection_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.connection_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        when (val current = state) {
            AuthState.NotConfigured -> Text(stringResource(R.string.connection_not_configured), color = MaterialTheme.colorScheme.error)
            AuthState.SignedOut -> Button(onClick = { activity?.let(viewModel::signIn) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_sign_in))
            }
            AuthState.SigningIn -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.connection_signing_in))
                TextButton(onClick = viewModel::signOut) { Text(stringResource(android.R.string.cancel)) }
            }
            AuthState.SignedIn -> {
                Text(stringResource(R.string.connection_signed_in), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.connection_sign_out))
                }
            }
            AuthState.NeedsReauth -> {
                Text(stringResource(R.string.connection_needs_reauth), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { activity?.let(viewModel::signIn) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.connection_sign_in_again))
                }
            }
            is AuthState.Error -> {
                Text(current.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.dismissError(); activity?.let(viewModel::signIn) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.connection_try_again))
                }
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }
}
