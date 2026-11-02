package org.monogram.feature.profile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.monogram.core.common.Outcome
import org.monogram.core.models.Message
import org.monogram.core.models.PeerId
import org.monogram.core.models.peerAvatarCacheKey
import org.monogram.core.ui.components.AppStatusBanner
import org.monogram.core.ui.components.AppSyncStatus
import org.monogram.core.ui.components.MediaPreviewViewer
import org.monogram.core.ui.components.ProfileSkeleton
import org.monogram.core.ui.loading.MonogramRefreshBox
import org.monogram.core.ui.rememberCacheGeneration
import org.monogram.feature.profile.DEFAULT_PROFILE_BACKGROUND_TRANSPARENCY
import org.monogram.feature.profile.ProfileBackgroundStorage
import org.monogram.feature.profile.ProfileComponent
import org.monogram.feature.profile.normalizeProfileBackgroundTransparency
import org.monogram.feature.profile.R
import org.monogram.network.http.MediaPriority
import org.monogram.network.http.MediaRepository
import org.monogram.network.http.peerPhotoSmallCacheKey

private val ProfileAtTopTolerance = 12
private const val ProfileAvatarCollapsedScale = 0.82f
private const val ProfilePullRefreshGraceMillis = 700L

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileContent(component: ProfileComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val profile = state.profile
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val backgroundAccountId = profile?.takeIf { it.isSelf }?.id?.value
    val savedBackground = remember(context, backgroundAccountId) {
        backgroundAccountId?.let { ProfileBackgroundStorage.load(context, it) }
    }
    var backgroundFile by remember(backgroundAccountId) { mutableStateOf(savedBackground?.file) }
    var backgroundTransparency by remember(backgroundAccountId) {
        mutableFloatStateOf(savedBackground?.transparency ?: DEFAULT_PROFILE_BACKGROUND_TRANSPARENCY)
    }
    var showCustomization by rememberSaveable(backgroundAccountId) { mutableStateOf(false) }
    var importingBackground by remember { mutableStateOf(false) }
    var backgroundImportError by remember { mutableStateOf(false) }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val accountId = backgroundAccountId
        if (uri != null && accountId != null) {
            scope.launch {
                importingBackground = true
                backgroundImportError = false
                var importedFile: java.io.File? = null
                try {
                    val imported = ProfileBackgroundStorage.importBackground(context, accountId, uri)
                    importedFile = imported
                    val previous = ProfileBackgroundStorage.select(context, accountId, imported)
                    backgroundFile = imported
                    previous?.let { old -> scope.launch(Dispatchers.IO) { old.delete() } }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    importedFile?.let { file -> withContext(Dispatchers.IO) { file.delete() } }
                    backgroundImportError = true
                } finally {
                    importingBackground = false
                }
            }
        }
    }
    var showAvatar by rememberSaveable { mutableStateOf(false) }
    var selectedMedia by remember { mutableStateOf<Message?>(null) }
    var avatarAttempt by remember(profile?.id?.value) { mutableIntStateOf(0) }
    val avatarCacheKey = profile?.avatarCacheKey?.let { cacheKey ->
        profile.id.let { peerAvatarCacheKey(it, cacheKey) }
    }
    val avatarCacheGeneration = rememberCacheGeneration(
        remember(component.mediaRepository, avatarCacheKey) {
            avatarCacheKey?.let { component.mediaRepository?.cacheGeneration(it) }
        },
    )
    val avatar = rememberProfileAvatar(
        repository = component.mediaRepository,
        peer = profile?.id,
        cacheKey = profile?.avatarCacheKey,
        cacheGeneration = avatarCacheGeneration,
        attempt = avatarAttempt,
    )
    val avatarFile = (avatar as? ProfileAvatar.Ready)?.file

    var pullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(pullRefreshing) {
        if (!pullRefreshing) return@LaunchedEffect
        val started = withTimeoutOrNull(ProfilePullRefreshGraceMillis) {
            snapshotFlow { state.loading }.first { it }
        } != null
        if (started) snapshotFlow { state.loading }.first { !it }
        pullRefreshing = false
    }

    val listState = rememberLazyListState()
    val atTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset <= ProfileAtTopTolerance
        }
    }
    val avatarScale by animateFloatAsState(
        targetValue = if (atTop) 1f else ProfileAvatarCollapsedScale,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "profileAvatarScale",
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ProfileTopBar(
                atTop = atTop,
                title = profile?.title,
                onBack = component::onBack,
                onCustomize = if (profile?.isSelf == true) {
                    {
                        backgroundImportError = false
                        showCustomization = true
                    }
                } else {
                    null
                },
            )
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (profile?.isSelf == true && !showCustomization) {
                backgroundFile?.takeIf { it.isFile }?.let { file ->
                    ProfileBackgroundLayer(
                        file = file,
                        transparency = backgroundTransparency,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = inner.calculateTopPadding()),
            ) {
                AppStatusBanner(
                    sync = AppSyncStatus.Hidden,
                    error = state.error,
                    onRetry = component::onRefresh,
                )
                MonogramRefreshBox(
                    isRefreshing = pullRefreshing,
                    onRefresh = {
                        pullRefreshing = true
                        component.onRefresh()
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AnimatedContent(
                        targetState = profile != null,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        label = "profileContentTransition",
                    ) { hasProfile ->
                        if (hasProfile && profile != null) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 24.dp,
                                ),
                            ) {
                                item(key = "header") {
                                    ProfileHeader(
                                        profile = profile,
                                        avatarFile = avatarFile,
                                        avatarState = avatar,
                                        avatarGeneration = avatarAttempt,
                                        avatarScale = avatarScale,
                                        mediaRepository = component.mediaRepository,
                                        onOpenAvatar = { showAvatar = true },
                                        onRetryAvatar = { avatarAttempt++ },
                                        onMessage = component::onMessage,
                                        onCopyUsername = profile.username?.takeIf { it.isNotBlank() }?.let { handle ->
                                            { scope.launch { clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("text", "@$handle"))) } }
                                        },
                                        snackbar = snackbar,
                                        clipboard = clipboard,
                                        scope = scope,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                item(key = "panels") {
                                    Box(modifier = Modifier.animateItem()) {
                                        ProfilePanelStrip(
                                            state = state,
                                            onSelect = component::onSelectPanel,
                                        )
                                    }
                                }
                                profilePanelContent(
                                    state = state,
                                    component = component,
                                    onOpenMedia = { selectedMedia = it },
                                )
                            }
                        } else if (state.loading) {
                            ProfileSkeleton()
                        }
                    }
                }
            }
        }
    }
    if (showAvatar) {
        MediaPreviewViewer(
            file = avatarFile,
            stateKey = "avatar:${profile?.id?.value}:${avatarFile?.absolutePath}",
            fallbackTitle = profile?.title.orEmpty(),
            contentDescription = stringResource(R.string.profile_open_photo),
            loop = true,
            onDismiss = { showAvatar = false },
        )
    }
    selectedMedia?.let { message ->
        ProfileMediaViewer(
            message = message,
            repository = component.mediaRepository,
            onDismiss = { selectedMedia = null },
        )
    }
    if (showCustomization && profile?.isSelf == true && backgroundAccountId != null) {
        val accountId = backgroundAccountId
        ProfileCustomizationSheet(
            profileTitle = profile.title,
            backgroundFile = backgroundFile?.takeIf { it.isFile },
            transparency = backgroundTransparency,
            busy = importingBackground,
            error = backgroundImportError,
            onChooseBackground = {
                backgroundImportError = false
                backgroundPicker.launch(arrayOf("image/*", "video/mp4"))
            },
            onRemoveBackground = {
                val previous = ProfileBackgroundStorage.clear(context, accountId)
                backgroundFile = null
                backgroundTransparency = DEFAULT_PROFILE_BACKGROUND_TRANSPARENCY
                backgroundImportError = false
                scope.launch(Dispatchers.IO) { previous?.delete() }
            },
            onTransparencyChange = { value ->
                backgroundTransparency = normalizeProfileBackgroundTransparency(value)
            },
            onTransparencyChangeFinished = {
                ProfileBackgroundStorage.setTransparency(context, accountId, backgroundTransparency)
            },
            onDismiss = {
                ProfileBackgroundStorage.setTransparency(context, accountId, backgroundTransparency)
                showCustomization = false
            },
        )
    }
}

internal sealed interface ProfileAvatar {
    data object Idle : ProfileAvatar
    data object Loading : ProfileAvatar
    data class Ready(val file: java.io.File) : ProfileAvatar
    data object Failed : ProfileAvatar
}

@Composable
private fun rememberProfileAvatar(
    repository: MediaRepository?,
    peer: PeerId?,
    cacheKey: String?,
    cacheGeneration: Long,
    attempt: Int,
): ProfileAvatar {
    if (peer == null || cacheKey == null) return ProfileAvatar.Idle
    var state by remember(peer, cacheKey, attempt) { mutableStateOf<ProfileAvatar>(ProfileAvatar.Loading) }
    LaunchedEffect(peer, cacheKey, attempt, cacheGeneration, repository) {
        val repo = repository ?: return@LaunchedEffect
        val key = peerAvatarCacheKey(peer, cacheKey)
        val smallKey = peerPhotoSmallCacheKey(key)
        fun showCachedFull(): Boolean {
            val full = repo.cachedFile(key)
            if (full == null) return false
            state = ProfileAvatar.Ready(full)
            return true
        }
        if (showCachedFull()) return@LaunchedEffect
        repeat(3) { attempt ->
            if (showCachedFull()) return@LaunchedEffect
            if (state !is ProfileAvatar.Ready) {
                repo.cachedFile(smallKey)?.let { state = ProfileAvatar.Ready(it) }
                    ?: repo.cachedAvatar(peer)?.let { state = ProfileAvatar.Ready(it) }
            }
            if (state !is ProfileAvatar.Ready) state = ProfileAvatar.Loading
            when (val result = repo.ensureLocalAvatar(peer, key, MediaPriority.USER)) {
                is Outcome.Ok -> {
                    if (!showCachedFull()) state = ProfileAvatar.Ready(result.value)
                    return@LaunchedEffect
                }
                is Outcome.Err -> {
                    if (showCachedFull() || state is ProfileAvatar.Ready) return@LaunchedEffect
                    if (attempt == 2) state = ProfileAvatar.Failed
                    else delay(400L * (attempt + 1))
                }
            }
        }
    }
    return state
}
