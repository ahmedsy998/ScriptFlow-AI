package com.example.data.local

import com.example.domain.model.StudioProject
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectRepository(private val dao: StudioDao) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(StudioProject::class.java)

    val allProjects: Flow<List<StudioProject>> = dao.observeAllProjects().map { list ->
        list.mapNotNull { entity -> runCatching { adapter.fromJson(entity.payloadJson) }.getOrNull() }
    }

    suspend fun saveProject(project: StudioProject): StudioProject = withContext(Dispatchers.IO) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val json = adapter.toJson(updated)
        val preview = updated.scriptText.trim().take(140)
        dao.upsertProject(
            ProjectEntity(
                id = updated.id,
                title = updated.title.ifBlank { "مشروع بدون عنوان" },
                scriptPreview = preview,
                visualStyleId = updated.visualStyle.id,
                sceneCount = updated.scenes.size,
                isDraft = updated.isDraft,
                createdAt = updated.createdAt,
                updatedAt = updated.updatedAt,
                lastExportedMp4Path = updated.lastExportedMp4Path,
                payloadJson = json
            )
        )
        updated
    }

    suspend fun getProject(id: String): StudioProject? = withContext(Dispatchers.IO) {
        dao.getProjectById(id)?.let { entity ->
            runCatching { adapter.fromJson(entity.payloadJson) }.getOrNull()
        }
    }

    suspend fun getLatestDraft(): StudioProject? = withContext(Dispatchers.IO) {
        dao.getLatestDraft()?.let { entity ->
            runCatching { adapter.fromJson(entity.payloadJson) }.getOrNull()
        }
    }

    suspend fun duplicateProject(project: StudioProject): StudioProject {
        val copy = project.copy(
            id = UUID.randomUUID().toString(),
            title = "${project.title} (نسخة)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastExportedMp4Path = null
        )
        return saveProject(copy)
    }

    suspend fun renameProject(project: StudioProject, newTitle: String): StudioProject {
        return saveProject(project.copy(title = newTitle.trim().ifEmpty { project.title }))
    }

    suspend fun deleteProject(project: StudioProject) = withContext(Dispatchers.IO) {
        dao.deleteProjectById(project.id)
        project.lastExportedMp4Path?.let { path ->
            runCatching { File(path).takeIf { it.exists() }?.delete() }
        }
    }

    fun exportProjectToJson(project: StudioProject): String {
        return adapter.indent("  ").toJson(project)
    }

    fun importProjectFromJson(json: String): Result<StudioProject> {
        return runCatching {
            val parsed = adapter.fromJson(json)
                ?: throw IllegalArgumentException("ملف المشروع غير صالح")
            parsed.copy(
                id = UUID.randomUUID().toString(),
                title = "${parsed.title} (مستورد)",
                updatedAt = System.currentTimeMillis(),
                lastExportedMp4Path = null
            )
        }
    }
}
