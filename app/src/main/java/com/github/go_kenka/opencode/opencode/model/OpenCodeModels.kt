/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.go_kenka.opencode.opencode.model

import java.util.UUID

data class OpenCodeService(
    val serviceName: String,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
) {
    val baseUrl: String = "http://$host:$port"
}

data class OpenCodeHealth(
    val healthy: Boolean,
    val version: String? = null,
)

data class OpenCodeSession(
    val id: String,
    val projectID: String?,
    val directory: String?,
    val title: String?,
    val time: Long,
    val gitBranch: String? = null,
    val gitStatusSummary: String? = null,
)

data class OpenCodeProject(
    val id: String,
    val name: String,
    val directory: String,
    val sessionId: String? = null,
    val isLocalOnly: Boolean = false,
    val gitBranch: String? = null,
    val gitStatusSummary: String? = null,
) {
    fun withSession(session: OpenCodeSession?): OpenCodeProject {
        return copy(sessionId = session?.id ?: sessionId)
    }

    companion object {
        fun fromSessions(sessions: List<OpenCodeSession>): List<OpenCodeProject> {
            return sessions
                .filter { it.directory != null }
                .groupBy { it.directory.orEmpty() }
                .mapNotNull { (directoryKey, projectSessions) ->
                    val latest = projectSessions.maxByOrNull { it.time } ?: return@mapNotNull null
                    val directory = latest.directory ?: return@mapNotNull null
                    OpenCodeProject(
                        id = latest.projectID ?: localProjectId(directoryKey),
                        name = directory.substringAfterLast('/').ifBlank { directory },
                        directory = directory,
                        sessionId = latest.id,
                        isLocalOnly = true,
                        gitBranch = latest.gitBranch,
                        gitStatusSummary = latest.gitStatusSummary,
                    )
                }
                .sortedByDescending { project ->
                    sessions.firstOrNull { it.id == project.sessionId }?.time ?: 0L
                }
        }

        private fun localProjectId(directory: String): String = "local::$directory"

        fun mergeWithSessions(
            projects: List<OpenCodeProject>,
            sessions: List<OpenCodeSession>,
        ): List<OpenCodeProject> {
            if (projects.isEmpty()) return fromSessions(sessions)

            val missingProjects = fromSessions(sessions).filter { candidate ->
                projects.none { existing ->
                    existing.id == candidate.id || existing.directory == candidate.directory
                }
            }
            if (missingProjects.isEmpty()) return projects

            return (projects + missingProjects)
                .distinctBy { "${it.id}|${it.directory}" }
        }
    }
}

data class OpenCodeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val reasoningContent: String = "",
    val reasoningCompleted: Boolean = true,
    val isError: Boolean = false,
    val time: Long = System.currentTimeMillis(),
)

data class OpenCodePermissionRequest(
    val sessionId: String,
    val permissionId: String,
    val title: String,
)

data class OpenCodeTodoItem(
    val id: String,
    val text: String,
    val done: Boolean,
)

enum class OpenCodePermissionResponse(val wireValue: String) {
    ONCE("once"),
    ALWAYS("always"),
    DENY("reject"),
}

data class OpenCodeDirectoryEntry(
    val name: String,
    val directory: String,
)

data class OpenCodeVcsInfo(
    val branch: String? = null,
    val statusSummary: String? = null,
)

data class OpenCodeMode(
    val agent: String,
    val label: String = agent,
) {
    companion object {
        val defaults = listOf(
            OpenCodeMode(agent = "build", label = "build"),
            OpenCodeMode(agent = "plan", label = "plan"),
        )

        val defaultBuild = defaults.first()
    }
}

data class OpenCodeModelOption(
    val providerID: String?,
    val modelID: String?,
    val label: String,
    val providerName: String? = null,
    val isFree: Boolean = false,
) {
    val displayLabel: String
        get() = if (isFree) "$label (Free)" else label

    companion object {
        val defaults = listOf(
            OpenCodeModelOption(
                providerID = "google",
                modelID = "gemini-3-pro-preview",
                label = "Gemini 3 Pro Preview",
                providerName = "Google",
            ),
        )
    }
}

enum class ThinkingLevel(val label: String) {
    DEFAULT("Default"),
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
}

data class OpenCodeUiState(
    val discoveryStatus: String = "Searching OpenCode services via mDNS...",
    val discoveredServices: List<OpenCodeService> = emptyList(),
    val pendingServiceSelection: Boolean = false,
    val pendingService: OpenCodeService? = null,
    val service: OpenCodeService? = null,
    val healthVersion: String? = null,
    val recentSessions: List<OpenCodeSession> = emptyList(),
    val projects: List<OpenCodeProject> = emptyList(),
    val selectedProject: OpenCodeProject? = null,
    val localProjects: List<OpenCodeProject> = emptyList(),
    val isProjectPickerVisible: Boolean = false,
    val isProjectPickerLoading: Boolean = false,
    val projectPickerCurrentDirectory: String? = null,
    val projectPickerQuery: String = "",
    val projectPickerDirectories: List<OpenCodeDirectoryEntry> = emptyList(),
    val projectPickerErrorMessage: String? = null,
    val messages: List<OpenCodeChatMessage> = emptyList(),
    val availableModes: List<OpenCodeMode> = OpenCodeMode.defaults,
    val selectedMode: OpenCodeMode = OpenCodeMode.defaultBuild,
    val models: List<OpenCodeModelOption> = OpenCodeModelOption.defaults,
    val selectedModel: OpenCodeModelOption = OpenCodeModelOption.defaults.first(),
    val selectedThinkingLevel: ThinkingLevel = ThinkingLevel.DEFAULT,
    val todos: List<OpenCodeTodoItem> = emptyList(),
    val pendingPermission: OpenCodePermissionRequest? = null,
    val isRespondingPermission: Boolean = false,
    val isConnecting: Boolean = true,
    val isHistoryLoading: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)
