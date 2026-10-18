package com.jake.duolauncher.appearance

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*

import com.jake.duolauncher.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun AppearanceSettings(state: AppearanceState, onMode: (AppearanceMode) -> Unit,
    onManual: (String, Double, Double) -> Unit, onDeviceLocation: () -> Unit, onClear: () -> Unit,
    onCancelDeviceLocation: () -> Unit = {}) {
    var place by remember(state.place) { mutableStateOf(state.place) }
    var latitude by remember(state.latitude) { mutableStateOf(state.latitude?.toString().orEmpty()) }
    var longitude by remember(state.longitude) { mutableStateOf(state.longitude?.toString().orEmpty()) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("appearance-settings")) {
        Text(stringResource(com.jake.duolauncher.R.string.appearance), style = MaterialTheme.typography.titleMedium)
        AppearanceMode.entries.forEach { mode ->
            GlassChip(selected = state.mode == mode, onClick = { onMode(mode) }, label = { Text(when (mode) {
                AppearanceMode.LIGHT -> "Light"; AppearanceMode.DARK -> "Dark"; AppearanceMode.SYSTEM -> "Follow system"
                AppearanceMode.SUNRISE_SUNSET -> "Sunrise / sunset"
        }) }, modifier = Modifier.testTag("appearance-${mode.name.lowercase()}"))
        }
        if (state.mode == AppearanceMode.SUNRISE_SUNSET) {
            state.fallback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            GlassField(place, { place = it }, Modifier.testTag("appearance-place"), label = { Text(stringResource(com.jake.duolauncher.R.string.place_name)) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassField(latitude, { latitude = it }, Modifier.weight(1f).testTag("appearance-latitude"), label = { Text(stringResource(com.jake.duolauncher.R.string.latitude_90_to_90)) }, singleLine = true)
                GlassField(longitude, { longitude = it }, Modifier.weight(1f).testTag("appearance-longitude"), label = { Text(stringResource(com.jake.duolauncher.R.string.longitude_180_to_180)) }, singleLine = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(prominent = true, onClick = {
                    focusManager.clearFocus(); keyboard?.hide()
                    when (val parsed = PlaceInput.parse(latitude, longitude)) {
                        is PlaceInput.Result.Ok -> {
                            // Show the cleaned-up numbers in the fields, so a pasted pair or a comma decimal is visibly understood.
                            latitude = parsed.latitude.toString(); longitude = parsed.longitude.toString()
                            inputError = null; onManual(place, parsed.latitude, parsed.longitude)
                        }
                        is PlaceInput.Result.Bad -> inputError = parsed.message
                    } },
                    modifier = Modifier.fillMaxWidth().testTag("appearance-save-place")) { Text(stringResource(com.jake.duolauncher.R.string.use_this_place)) }
                AppearanceFeedback(inputError, MaterialTheme.colorScheme.error, "appearance-manual-status")
                GlassButton(onClick = {
                    focusManager.clearFocus(); keyboard?.hide(); onDeviceLocation()
                }, modifier = Modifier.fillMaxWidth()
                    .testTag("appearance-device-location")) { Text(stringResource(com.jake.duolauncher.R.string.use_device_location)) }
                AppearanceFeedback(state.locationStatus, MaterialTheme.colorScheme.onSurfaceVariant,
                    "appearance-location-status")
                if (state.locationStatus?.startsWith("Waiting") == true) GlassButton(onClick = onCancelDeviceLocation,
                    Modifier.fillMaxWidth().testTag("appearance-location-cancel")) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) }
                if (state.latitude != null && state.longitude != null) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val saved = remember(state.place, state.latitude, state.longitude) {
                        PlaceInput.describe(state.place, state.latitude, state.longitude, java.time.LocalDate.now(),
                            java.time.ZoneId.systemDefault(), android.text.format.DateFormat.is24HourFormat(context))
                    }
                    Text(saved, Modifier.testTag("appearance-saved-place").semantics { liveRegion = LiveRegionMode.Polite })
                    Text(stringResource(com.jake.duolauncher.R.string.kept_only_on_this_device_never), style = MaterialTheme.typography.bodySmall)
                    GlassButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text(stringResource(com.jake.duolauncher.R.string.clear_location)) }
                }
            }
        }
    }
}

@Composable
private fun AppearanceFeedback(message: String?, color: androidx.compose.ui.graphics.Color, tag: String) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { if (message != null) bringIntoView.bringIntoView() }
    message?.let {
        Text(it, color = color, modifier = Modifier.bringIntoViewRequester(bringIntoView)
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag))
    }
}
