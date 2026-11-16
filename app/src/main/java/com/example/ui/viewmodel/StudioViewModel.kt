package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppPreferences
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioSettingsStore
import com.example.domain.engine.ScriptAnalysisEngine
import com.example.domain.model.ArabicDialect
import com.example.domain.model.KenBurnsMode
import com.example.domain.model.ProjectTemplate
import com.example.domain.model.RenderProgressState
import com.example.domain.model.SceneAsset
import com.example.domain.model.ScriptMetrics
import com.example.domain.model.StoryboardScene
import com.example.domain.model.StudioProject
import com.example.domain.model.TeleprompterConfig
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoConfig
import com.example.domain.model.VideoResolution
import com.example.domain.model.VisualStyle
import com.example.domain.model.VoiceConfig
import com.example.engine.audio.ArabicTtsEngine
import com.example.engine.audio.TtsEngineStatus
import com.example.engine.video.DeviceVideoCapabilities
import com.example.engine.video.VideoRenderEngine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(FlowPreview::class)
class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val db = StudioDatabase.getInstance(application)
    private val repository = ProjectRepository(db.studioDao())
    private val settingsStore = StudioSettingsStore(application)
    val ttsEngine = ArabicTtsEngine(application)
    private val renderEngine = VideoRenderEngine(application, ttsEngine)

    val deviceCapabilities: DeviceVideoCapabilities = renderEngine.inspectDeviceCapabilities()
    val ttsStatus: StateFlow<TtsEngineStatus> = ttsEngine.status
    val isVoicePreviewPlaying: StateFlow<Boolean> = ttsEngine.isSpeaking

    val appPreferences: StateFlow<AppPreferences> = settingsStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val allProjects: StateFlow<List<StudioProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentProject = MutableStateFlow(StudioProject())
    val currentProject: StateFlow<StudioProject> = _currentProject.asStateFlow()

    val scriptMetrics: StateFlow<ScriptMetrics> = _currentProject
        .combine(settingsStore.preferencesFlow) { project, _ ->
            ScriptAnalysisEngine.analyzeMetrics(
                scriptText = project.scriptText,
                voiceConfig = project.voiceConfig,
                videoConfig = project.videoConfig
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScriptMetrics())

    private val _isStoryboardEditorOpen = MutableStateFlow(false)
    val isStoryboardEditorOpen: StateFlow<Boolean> = _isStoryboardEditorOpen.asStateFlow()

    private val _renderState = MutableStateFlow<RenderProgressState>(RenderProgressState.Idle)
    val renderState: StateFlow<RenderProgressState> = _renderState.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _lastSavedDraft = MutableStateFlow<StudioProject?>(null)
    val lastSavedDraft: StateFlow<StudioProject?> = _lastSavedDraft.asStateFlow()

    private val undoStack = ArrayDeque<List<StoryboardScene>>()
    private val redoStack = ArrayDeque<List<StoryboardScene>>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var exportJob: Job? = null

    init {
        restoreLastSession()
        setupAutoSave()
    }

    private fun restoreLastSession() {
        viewModelScope.launch {
            val prefs = settingsStore.preferencesFlow.first()
            val activeId = prefs.activeProjectId
            val restored = if (!activeId.isNullOrBlank()) repository.getProject(activeId) else null
            if (restored != null) {
                _currentProject.value = restored
            } else {
                _currentProject.value = StudioProject(
                    voiceConfig = VoiceConfig(dialect = prefs.defaultDialect),
                    videoConfig = VideoConfig(resolution = prefs.defaultResolution)
                )
            }
            _lastSavedDraft.value = repository.getLatestDraft()
        }
    }

    private fun setupAutoSave() {
        viewModelScope.launch {
            _currentProject
                .drop(1)
                .debounce(900L)
                .collect { project ->
                    val hasContent = project.scriptText.isNotBlank() || project.scenes.isNotEmpty()
                    if (appPreferences.value.autoSaveEnabled && hasContent) {
                        val saved = repository.saveProject(project)
                        settingsStore.setActiveProjectId(saved.id)
                        if (saved.isDraft) _lastSavedDraft.value = saved
                    }
                }
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    fun showMessage(msg: String) {
        _uiMessage.value = msg
    }

    // --- Script & Config Mutations ---

    fun updateProjectTitle(title: String) {
        _currentProject.value = _currentProject.value.copy(title = title)
    }

    fun updateScriptText(text: String) {
        val current = _currentProject.value
        val autoTitle = if (current.title == "مشروع جديد" && text.trim().length > 4) {
            text.trim().lines().firstOrNull()?.take(36)?.trim() ?: current.title
        } else {
            current.title
        }
        _currentProject.value = current.copy(scriptText = text, title = autoTitle)
    }

    fun updateVoiceConfig(transform: (VoiceConfig) -> VoiceConfig) {
        _currentProject.value = _currentProject.value.copy(
            voiceConfig = transform(_currentProject.value.voiceConfig)
        )
    }

    fun updateVisualStyle(style: VisualStyle) {
        val current = _currentProject.value
        val updatedScenes = current.scenes.map { it.copy(visualStyle = style) }
        _currentProject.value = current.copy(
            visualStyle = style,
            scenes = updatedScenes
        )
    }

    fun updateVideoConfig(transform: (VideoConfig) -> VideoConfig) {
        _currentProject.value = _currentProject.value.copy(
            videoConfig = transform(_currentProject.value.videoConfig)
        )
    }

    fun updateTeleprompterConfig(transform: (TeleprompterConfig) -> TeleprompterConfig) {
        _currentProject.value = _currentProject.value.copy(
            teleprompterConfig = transform(_currentProject.value.teleprompterConfig)
        )
    }

    // --- Voice Preview ---

    fun toggleVoicePreview() {
        if (ttsEngine.isSpeaking.value) {
            ttsEngine.stopPreview()
            return
        }
        val project = _currentProject.value
        val previewSentence = project.scriptText.trim()
            .split(Regex("""[.!؟?\n]"""))
            .firstOrNull { it.trim().length >= 4 }
            ?.trim()
            ?: project.voiceConfig.dialect.samplePhrase

        val cleanText = if (project.voiceConfig.arabicPronunciationCleanup) {
            ScriptAnalysisEngine.prepareArabicPronunciation(previewSentence)
        } else {
            previewSentence
        }

        viewModelScope.launch {
            val started = ttsEngine.previewVoice(cleanText, project.voiceConfig)
            if (!started) {
                _uiMessage.value = "تعذر تشغيل معاينة الصوت. تأكد من توفر محرك النطق على الجهاز."
            }
        }
    }

    fun openSystemTtsInstaller(): Intent = ttsEngine.createInstallTtsDataIntent()

    fun refreshTtsCapabilities() = ttsEngine.refreshCapabilities()

    // --- Storyboard Generation & Editing ---

    fun createVideoStoryboard() {
        val project = _currentProject.value
        if (project.scriptText.isBlank()) {
            _uiMessage.value = "يرجى كتابة أو لصق السكربت أولاً لإنشاء المشاهد."
            return
        }
        val generatedScenes = ScriptAnalysisEngine.generateStoryboard(
            scriptText = project.scriptText,
            visualStyle = project.visualStyle,
            voiceConfig = project.voiceConfig,
            videoConfig = project.videoConfig
        )
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()

        val updated = project.copy(scenes = generatedScenes, isDraft = false)
        _currentProject.value = updated
        _isStoryboardEditorOpen.value = true

        viewModelScope.launch {
            val saved = repository.saveProject(updated)
            settingsStore.setActiveProjectId(saved.id)
        }
    }

    fun openStoryboardEditorIfReady() {
        if (_currentProject.value.scenes.isEmpty() && _currentProject.value.scriptText.isNotBlank()) {
            createVideoStoryboard()
        } else {
            _isStoryboardEditorOpen.value = true
        }
    }

    fun closeStoryboardEditor() {
        _isStoryboardEditorOpen.value = false
    }

    private fun pushUndoSnapshot() {
        undoStack.addLast(_currentProject.value.scenes)
        if (undoStack.size > 25) undoStack.removeFirst()
        redoStack.clear()
        syncUndoRedoFlags()
    }

    private fun syncUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undoStoryboard() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_currentProject.value.scenes)
        val previous = undoStack.removeLast()
        _currentProject.value = _currentProject.value.copy(scenes = renumberScenes(previous))
        syncUndoRedoFlags()
    }

    fun redoStoryboard() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_currentProject.value.scenes)
        val next = redoStack.removeLast()
        _currentProject.value = _currentProject.value.copy(scenes = renumberScenes(next))
        syncUndoRedoFlags()
    }

    fun mutateScenes(transform: (List<StoryboardScene>) -> List<StoryboardScene>) {
        pushUndoSnapshot()
        val updated = renumberScenes(transform(_currentProject.value.scenes))
        _currentProject.value = _currentProject.value.copy(scenes = updated)
    }

    fun updateScene(sceneId: String, updater: (StoryboardScene) -> StoryboardScene) {
        mutateScenes { list -> list.map { if (it.id == sceneId) updater(it) else it } }
    }

    fun moveScene(fromIndex: Int, toIndex: Int) {
        val current = _currentProject.value.scenes
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        mutateScenes { list ->
            list.toMutableList().apply {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
    }

    fun duplicateScene(sceneId: String) {
        mutateScenes { list ->
            val idx = list.indexOfFirst { it.id == sceneId }
            if (idx < 0) return@mutateScenes list
            val clone = list[idx].copy(id = UUID.randomUUID().toString())
            list.toMutableList().apply { add(idx + 1, clone) }
        }
    }

    fun deleteScene(sceneId: String) {
        if (_currentProject.value.scenes.size <= 1) {
            _uiMessage.value = "يجب أن يحتوي الفيديو على مشهد واحد على الأقل."
            return
        }
        mutateScenes { list -> list.filterNot { it.id == sceneId } }
    }

    fun regenerateScene(sceneId: String) {
        val project = _currentProject.value
        val target = project.scenes.firstOrNull { it.id == sceneId } ?: return
        val regenerated = ScriptAnalysisEngine.generateStoryboard(
            scriptText = target.originalSegment,
            visualStyle = project.visualStyle,
            voiceConfig = project.voiceConfig,
            videoConfig = project.videoConfig
        ).firstOrNull() ?: return

        updateScene(sceneId) {
            it.copy(
                visualDescription = regenerated.visualDescription,
                keywords = regenerated.keywords,
                emotionalTone = regenerated.emotionalTone,
                waveformPeaks = regenerated.waveformPeaks,
                kenBurnsMode = KenBurnsMode.entries[(it.kenBurnsMode.ordinal + 1) % KenBurnsMode.entries.size]
            )
        }
    }

    fun addAssetToScene(sceneId: String, asset: SceneAsset) {
        updateScene(sceneId) { scene ->
            val filtered = scene.assets.filterNot { it.type == com.example.domain.model.SceneMediaType.PROCEDURAL }
            scene.copy(assets = filtered + asset)
        }
    }

    fun removeAssetFromScene(sceneId: String, assetId: String) {
        updateScene(sceneId) { scene ->
            val remaining = scene.assets.filterNot { it.id == assetId }
            scene.copy(assets = remaining.ifEmpty { listOf(SceneAsset()) })
        }
    }

    fun previewSceneAudio(scene: StoryboardScene) {
        viewModelScope.launch {
            if (ttsEngine.isSpeaking.value) {
                ttsEngine.stopPreview()
            } else {
                ttsEngine.previewVoice(scene.narrationText, _currentProject.value.voiceConfig)
            }
        }
    }

    fun updateSceneTransition(sceneId: String, transition: TransitionType) {
        updateScene(sceneId) { it.copy(transition = transition) }
    }

    fun updateSceneKenBurns(sceneId: String, mode: KenBurnsMode) {
        updateScene(sceneId) { it.copy(kenBurnsMode = mode) }
    }

    private fun renumberScenes(list: List<StoryboardScene>): List<StoryboardScene> {
        return list.mapIndexed { idx, scene -> scene.copy(sceneNumber = idx + 1) }
    }

    // --- MP4 Rendering & Export ---

    fun startMp4Export() {
        if (exportJob?.isActive == true) return
        val project = _currentProject.value
        if (project.scenes.isEmpty()) {
            _uiMessage.value = "قم بإنشاء لوحة المشاهد أولاً قبل التصدير."
            return
        }

        exportJob = viewModelScope.launch {
            renderEngine.renderProjectToMp4(project).collect { state ->
                _renderState.value = state
                if (state is RenderProgressState.Completed) {
                    val updated = _currentProject.value.copy(
                        lastExportedMp4Path = state.outputPath,
                        isDraft = false
                    )
                    _currentProject.value = updated
                    repository.saveProject(updated)
                }
            }
        }
    }

    fun cancelMp4Export() {
        exportJob?.cancel()
        exportJob = null
        _renderState.value = RenderProgressState.Cancelled
    }

    fun resetRenderState() {
        _renderState.value = RenderProgressState.Idle
    }

    fun saveExportedMp4ToGallery(mp4Path: String) {
        val file = File(mp4Path)
        if (!file.exists()) {
            _uiMessage.value = "ملف الفيديو غير موجود."
            return
        }
        viewModelScope.launch {
            val result = renderEngine.saveMp4ToDeviceGallery(file, _currentProject.value.title)
            _uiMessage.value = if (result.isSuccess) {
                "تم حفظ الفيديو في معرض الهاتف (Movies/ScriptFlowAI)."
            } else {
                "تعذر الحفظ في المعرض: ${result.exceptionOrNull()?.localizedMessage ?: ""}"
            }
        }
    }

    // --- Project Management & Templates ---

    fun createNewProject() {
        val prefs = appPreferences.value
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()
        _isStoryboardEditorOpen.value = false
        _currentProject.value = StudioProject(
            voiceConfig = VoiceConfig(dialect = prefs.defaultDialect),
            videoConfig = VideoConfig(resolution = prefs.defaultResolution)
        )
        viewModelScope.launch { settingsStore.setActiveProjectId(_currentProject.value.id) }
    }

    fun restoreDraft(draft: StudioProject) {
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()
        _currentProject.value = draft
        _isStoryboardEditorOpen.value = draft.scenes.isNotEmpty()
        viewModelScope.launch { settingsStore.setActiveProjectId(draft.id) }
    }

    fun openProject(project: StudioProject) {
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()
        _currentProject.value = project
        _isStoryboardEditorOpen.value = project.scenes.isNotEmpty()
        viewModelScope.launch { settingsStore.setActiveProjectId(project.id) }
    }

    fun loadTemplate(template: ProjectTemplate) {
        val prefs = appPreferences.value
        val newProj = StudioProject(
            title = template.titleAr,
            scriptText = template.sampleScript,
            visualStyle = template.visualStyle,
            voiceConfig = VoiceConfig(
                voiceId = template.voiceProfile.id,
                dialect = prefs.defaultDialect
            ),
            videoConfig = VideoConfig(
                aspectRatio = template.aspectRatio,
                resolution = prefs.defaultResolution
            ),
            isDraft = true
        )
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()
        _currentProject.value = newProj
        _isStoryboardEditorOpen.value = false
        viewModelScope.launch {
            repository.saveProject(newProj)
            settingsStore.setActiveProjectId(newProj.id)
        }
    }

    fun renameProject(project: StudioProject, newTitle: String) {
        viewModelScope.launch {
            val saved = repository.renameProject(project, newTitle)
            if (_currentProject.value.id == saved.id) {
                _currentProject.value = _currentProject.value.copy(title = saved.title)
            }
        }
    }

    fun duplicateProject(project: StudioProject) {
        viewModelScope.launch {
            repository.duplicateProject(project)
            _uiMessage.value = "تم تكرار المشروع بنجاح."
        }
    }

    fun deleteProject(project: StudioProject) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_currentProject.value.id == project.id) {
                createNewProject()
            }
            _uiMessage.value = "تم حذف المشروع."
        }
    }

    fun exportProjectConfigFile(project: StudioProject): File {
        val dir = File(getApplication<Application>().filesDir, "projects").apply { mkdirs() }
        val file = File(dir, "ScriptFlow_${project.id.take(8)}.json")
        file.writeText(repository.exportProjectToJson(project), Charsets.UTF_8)
        return file
    }

    fun importProjectFromUri(uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
            }.getOrNull()

            if (text.isNullOrBlank()) {
                _uiMessage.value = "تعذر قراءة ملف المشروع المحدد."
                return@launch
            }
            val parsed = repository.importProjectFromJson(text)
            if (parsed.isSuccess) {
                val imported = repository.saveProject(parsed.getOrThrow())
                openProject(imported)
                _uiMessage.value = "تم استيراد المشروع بنجاح."
            } else {
                _uiMessage.value = "صيغة ملف المشروع غير صالحة."
            }
        }
    }

    // --- Settings ---

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDarkTheme(enabled) }
    }

    fun setAutoSave(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoSave(enabled) }
    }

    fun setDefaultResolution(res: VideoResolution) {
        viewModelScope.launch { settingsStore.setDefaultResolution(res) }
    }

    fun setDefaultDialect(dialect: ArabicDialect) {
        viewModelScope.launch { settingsStore.setDefaultDialect(dialect) }
    }

    override fun onCleared() {
        super.onCleared()
        ttsEngine.shutdown()
    }
}
