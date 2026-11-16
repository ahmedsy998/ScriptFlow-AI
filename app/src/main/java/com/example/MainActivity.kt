package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ExportProgressAndPlayerDialog
import com.example.ui.screens.CreateStudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StoryboardEditorScreen
import com.example.ui.screens.TemplatesScreen
import com.example.ui.theme.ScriptFlowTheme
import com.example.ui.viewmodel.StudioViewModel

enum class MainNavTab(
    val titleAr: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("الرئيسية", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    CREATE("إنشاء فيديو", Icons.Filled.MovieCreation, Icons.Outlined.MovieCreation, "nav_create"),
    PROJECTS("مشاريعي", Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy, "nav_projects"),
    TEMPLATES("القوالب", Icons.Filled.AutoAwesomeMosaic, Icons.Outlined.AutoAwesomeMosaic, "nav_templates"),
    SETTINGS("الإعدادات", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: StudioViewModel = viewModel()
            val prefs by viewModel.appPreferences.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ScriptFlowTheme(darkTheme = prefs.isDarkTheme) {
                    ScriptFlowApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun ScriptFlowApp(viewModel: StudioViewModel) {
    val context = LocalContext.current
    val prefs by viewModel.appPreferences.collectAsStateWithLifecycle()
    val project by viewModel.currentProject.collectAsStateWithLifecycle()
    val metrics by viewModel.scriptMetrics.collectAsStateWithLifecycle()
    val projectsList by viewModel.allProjects.collectAsStateWithLifecycle()
    val latestDraft by viewModel.lastSavedDraft.collectAsStateWithLifecycle()
    val ttsStatus by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val isVoicePreviewPlaying by viewModel.isVoicePreviewPlaying.collectAsStateWithLifecycle()
    val isEditorOpen by viewModel.isStoryboardEditorOpen.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val renderState by viewModel.renderState.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MainNavTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail && !isEditorOpen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.titleAr
                                    )
                                },
                                label = { Text(tab.titleAr, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && !isEditorOpen) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.titleAr
                                    )
                                },
                                label = { Text(tab.titleAr) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (isEditorOpen && currentTab == MainNavTab.CREATE) {
                        StoryboardEditorScreen(
                            project = project,
                            canUndo = canUndo,
                            canRedo = canRedo,
                            ttsEngine = viewModel.ttsEngine,
                            onBack = { viewModel.closeStoryboardEditor() },
                            onUndo = { viewModel.undoStoryboard() },
                            onRedo = { viewModel.redoStoryboard() },
                            onMoveScene = { f, t -> viewModel.moveScene(f, t) },
                            onDuplicateScene = { id -> viewModel.duplicateScene(id) },
                            onDeleteScene = { id -> viewModel.deleteScene(id) },
                            onRegenerateScene = { id -> viewModel.regenerateScene(id) },
                            onUpdateScene = { id, updater -> viewModel.updateScene(id, updater) },
                            onAddAsset = { id, asset -> viewModel.addAssetToScene(id, asset) },
                            onRemoveAsset = { id, assetId -> viewModel.removeAssetFromScene(id, assetId) },
                            onPreviewSceneAudio = { scene -> viewModel.previewSceneAudio(scene) },
                            onExportMp4 = { viewModel.startMp4Export() }
                        )
                    } else {
                        when (currentTab) {
                            MainNavTab.HOME -> HomeScreen(
                                recentProjects = projectsList,
                                latestDraft = latestDraft,
                                ttsStatus = ttsStatus,
                                deviceCapabilities = viewModel.deviceCapabilities,
                                onStartNewProject = {
                                    viewModel.createNewProject()
                                    currentTab = MainNavTab.CREATE
                                },
                                onResumeDraft = { draft ->
                                    viewModel.restoreDraft(draft)
                                    currentTab = MainNavTab.CREATE
                                },
                                onOpenProject = { proj ->
                                    viewModel.openProject(proj)
                                    currentTab = MainNavTab.CREATE
                                },
                                onSelectTemplate = { tpl ->
                                    viewModel.loadTemplate(tpl)
                                    currentTab = MainNavTab.CREATE
                                }
                            )

                            MainNavTab.CREATE -> CreateStudioScreen(
                                project = project,
                                metrics = metrics,
                                latestDraft = latestDraft,
                                ttsStatus = ttsStatus,
                                isVoicePreviewPlaying = isVoicePreviewPlaying,
                                deviceCapabilities = viewModel.deviceCapabilities,
                                onTitleChange = { viewModel.updateProjectTitle(it) },
                                onScriptChange = { viewModel.updateScriptText(it) },
                                onVoiceConfigChange = { viewModel.updateVoiceConfig(it) },
                                onSelectVisualStyle = { viewModel.updateVisualStyle(it) },
                                onVideoConfigChange = { viewModel.updateVideoConfig(it) },
                                onTeleprompterConfigChange = { viewModel.updateTeleprompterConfig(it) },
                                onToggleVoicePreview = { viewModel.toggleVoicePreview() },
                                onInstallTtsData = {
                                    runCatching { context.startActivity(viewModel.openSystemTtsInstaller()) }
                                },
                                onRestoreDraft = { draft -> viewModel.restoreDraft(draft) },
                                onCreateVideoClicked = { viewModel.createVideoStoryboard() },
                                onOpenExistingStoryboard = { viewModel.openStoryboardEditorIfReady() }
                            )

                            MainNavTab.PROJECTS -> ProjectsScreen(
                                projects = projectsList,
                                onCreateNew = {
                                    viewModel.createNewProject()
                                    currentTab = MainNavTab.CREATE
                                },
                                onOpenProject = { proj ->
                                    viewModel.openProject(proj)
                                    currentTab = MainNavTab.CREATE
                                },
                                onRenameProject = { proj, title -> viewModel.renameProject(proj, title) },
                                onDuplicateProject = { proj -> viewModel.duplicateProject(proj) },
                                onDeleteProject = { proj -> viewModel.deleteProject(proj) },
                                onExportProjectJson = { proj -> viewModel.exportProjectConfigFile(proj) },
                                onImportProjectUri = { uri ->
                                    viewModel.importProjectFromUri(uri)
                                    currentTab = MainNavTab.CREATE
                                }
                            )

                            MainNavTab.TEMPLATES -> TemplatesScreen(
                                onUseTemplate = { tpl ->
                                    viewModel.loadTemplate(tpl)
                                    currentTab = MainNavTab.CREATE
                                }
                            )

                            MainNavTab.SETTINGS -> SettingsScreen(
                                preferences = prefs,
                                ttsStatus = ttsStatus,
                                deviceCapabilities = viewModel.deviceCapabilities,
                                onToggleDarkTheme = { viewModel.setDarkTheme(it) },
                                onToggleAutoSave = { viewModel.setAutoSave(it) },
                                onSelectDefaultResolution = { viewModel.setDefaultResolution(it) },
                                onSelectDefaultDialect = { viewModel.setDefaultDialect(it) },
                                onInstallTtsData = {
                                    runCatching { context.startActivity(viewModel.openSystemTtsInstaller()) }
                                },
                                onRefreshTts = { viewModel.refreshTtsCapabilities() }
                            )
                        }
                    }
                }
            }
        }
    }

    ExportProgressAndPlayerDialog(
        renderState = renderState,
        onCancel = { viewModel.cancelMp4Export() },
        onDismiss = { viewModel.resetRenderState() },
        onSaveToGallery = { path -> viewModel.saveExportedMp4ToGallery(path) },
        onRetry = { viewModel.startMp4Export() }
    )
}
