package org.monogram.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt
import org.monogram.feature.profile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileCustomizationSheet(
    profileTitle: String,
    backgroundFile: File?,
    transparency: Float,
    busy: Boolean,
    error: Boolean,
    onChooseBackground: () -> Unit,
    onRemoveBackground: () -> Unit,
    onTransparencyChange: (Float) -> Unit,
    onTransparencyChangeFinished: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_customize),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.profile_background_local_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                if (backgroundFile != null) {
                    ProfileBackgroundLayer(
                        file = backgroundFile,
                        transparency = transparency,
                        modifier = Modifier.matchParentSize(),
                    )
                    ProfilePreviewTitle(
                        title = profileTitle,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.profile_background_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (backgroundFile != null) {
                val transparencyLabel = stringResource(
                    R.string.profile_background_transparency,
                    (transparency * 100).roundToInt(),
                )
                Text(
                    text = transparencyLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = transparency,
                    onValueChange = onTransparencyChange,
                    onValueChangeFinished = onTransparencyChangeFinished,
                    valueRange = 0f..1f,
                    modifier = Modifier.semantics { contentDescription = transparencyLabel },
                )
            }
            FilledTonalButton(
                onClick = onChooseBackground,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(imageVector = Icons.Outlined.Image, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.profile_background_choose))
            }
            if (backgroundFile != null) {
                TextButton(
                    onClick = onRemoveBackground,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = null)
                    Text(
                        text = stringResource(R.string.profile_background_remove),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (error) {
                Text(
                    text = stringResource(R.string.profile_background_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ProfilePreviewTitle(title: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier.padding(16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
