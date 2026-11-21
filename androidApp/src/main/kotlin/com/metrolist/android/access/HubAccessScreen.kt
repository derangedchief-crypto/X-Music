package com.metrolist.android.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.metrolist.android.R

@Composable
internal fun HubAccessScreen(
    state: HubAccessState,
    access: HubAccess,
) {
    // Codes are not placed in saved-instance state, logs or analytics.
    var code by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.xmusic_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(state.message), modifier = Modifier.widthIn(max = 420.dp))
            if (state.checking) {
                CircularProgressIndicator()
                Text(stringResource(R.string.xmusic_access_checking))
            } else if (state.bound) {
                Button(onClick = access::refresh) { Text(stringResource(R.string.xmusic_access_retry)) }
                TextButton(onClick = access::signOut) { Text(stringResource(R.string.xmusic_access_change)) }
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { character -> character in '0'..'9' }.take(6) },
                    label = { Text(stringResource(R.string.xmusic_access_code)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { access.activate(code) }),
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                )
                Button(onClick = { access.activate(code) }, enabled = code.length == 6) {
                    Text(stringResource(R.string.xmusic_access_sign_in))
                }
            }
        }
    }
}
