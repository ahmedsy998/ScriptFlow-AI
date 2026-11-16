package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val scriptPreview: String,
    val visualStyleId: String,
    val sceneCount: Int,
    val isDraft: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastExportedMp4Path: String?,
    val payloadJson: String
)
