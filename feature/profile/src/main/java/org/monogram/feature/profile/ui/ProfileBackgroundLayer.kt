package org.monogram.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import org.monogram.core.ui.components.LoopingVideo

@Composable
internal fun ProfileBackgroundLayer(
    file: File,
    transparency: Float,
    modifier: Modifier = Modifier,
) {
    val surfaceAlpha = transparency.coerceIn(0f, 1f)
    Box(modifier) {
        if (surfaceAlpha < 1f) {
            if (file.extension.equals("mp4", ignoreCase = true)) {
                LoopingVideo(
                    file = file,
                    modifier = Modifier.matchParentSize(),
                    crop = true,
                    loop = true,
                    muted = true,
                )
            } else {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)),
        )
    }
}
