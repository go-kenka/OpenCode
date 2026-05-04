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

package com.github.go_kenka.opencode.opencode

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.go_kenka.opencode.R
import com.github.go_kenka.opencode.opencode.api.OpenCodeClient
import com.github.go_kenka.opencode.opencode.discovery.NsdOpenCodeDiscovery
import com.github.go_kenka.opencode.opencode.discovery.OpenCodeDiscovery
import com.github.go_kenka.opencode.opencode.discovery.OpenCodeDiscoveryState
import com.github.go_kenka.opencode.opencode.model.OpenCodeChatMessage
import com.github.go_kenka.opencode.opencode.model.OpenCodeDirectoryEntry
import com.github.go_kenka.opencode.opencode.model.OpenCodeMode
import com.github.go_kenka.opencode.opencode.model.OpenCodeModelOption
import com.github.go_kenka.opencode.opencode.model.OpenCodePermissionRequest
import com.github.go_kenka.opencode.opencode.model.OpenCodePermissionResponse
import com.github.go_kenka.opencode.opencode.model.OpenCodeProject
import com.github.go_kenka.opencode.opencode.model.OpenCodeService
import com.github.go_kenka.opencode.opencode.model.OpenCodeSession
import com.github.go_kenka.opencode.opencode.model.OpenCodeTodoItem
import com.github.go_kenka.opencode.opencode.model.OpenCodeUiState
import com.github.go_kenka.opencode.opencode.model.ThinkingLevel
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OpencodeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private fun tr(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    private val discovery: OpenCodeDiscovery = NsdOpenCodeDiscovery(application)
    private val client = OpenCodeClient()
    private val preferences = OpencodePreferences(application)

    private val _uiState = MutableStateFlow(OpenCodeUiState())
    private var connectJob: Job? = null
    private var sendJob: Job? = null
    private var sendingSessionId: String? = null
    private var projectPickerQueryJob: Job? = null
    private var sessions: List<OpenCodeSession> = emptyList()
    private val sessionTodoCache = linkedMapOf<String, List<OpenCodeTodoItem>>()
    private var discoveryObserverStarted = false

    val uiState: StateFlow<OpenCodeUiState> = _uiState.asStateFlow()

    init {
        val manualServices = preferences.getManualServices()
        if (manualServices.isNotEmpty()) {
            _uiState.update { it.copy(discoveredServices = manualServices) }
        }
        observeDiscovery()
    }

    fun startDiscovery() {
        observeDiscovery()
        discovery.start()
    }

    fun markDiscoveryPermissionDenied() {
        _uiState.update {
            it.copy(
                discoveryStatus = tr(R.string.ov_missing_nearby_permission),
                isConnecting = false,
                errorMessage = tr(R.string.ov_grant_permission_retry),
            )
        }
    }

    fun retryDiscovery() {
        connectJob?.cancel()
        sessions = emptyList()
        sessionTodoCache.clear()
        val manualServices = preferences.getManualServices()
        _uiState.value = OpenCodeUiState(
            discoveredServices = manualServices,
            discoveryStatus = tr(R.string.ov_rediscovering_service),
            isConnecting = true,
        )
        discovery.stop()
        startDiscovery()
    }

    fun dismissDiscoveredServicePicker() {
        _uiState.update { it.copy(pendingServiceSelection = false, pendingService = null) }
    }

    fun selectService(service: OpenCodeService) {
        preferences.saveSelectedServiceKey(service.key())
        _uiState.update {
            it.copy(
                pendingServiceSelection = false,
                pendingService = null,
                errorMessage = null,
            )
        }
        connectToService(service)
    }

    fun saveManualService(service: OpenCodeService, originalKey: String? = null) {
        val state = _uiState.value
        val existing = state.discoveredServices.toMutableList()
        val edited = existing.mapNotNull { candidate ->
            if (originalKey != null && candidate.key() == originalKey) {
                null
            } else {
                candidate
            }
        }
        val merged = (edited + service).distinctBy { it.key() }
        preferences.saveManualServices(merged)
        _uiState.update { it.copy(discoveredServices = merged) }
    }

    fun deleteManualService(service: OpenCodeService) {
        val state = _uiState.value
        val updated = state.discoveredServices.filterNot { it.key() == service.key() }
        preferences.saveManualServices(updated)
        _uiState.update {
            val clearCurrent = it.service?.key() == service.key()
            it.copy(
                discoveredServices = updated,
                service = if (clearCurrent) null else it.service,
                healthVersion = if (clearCurrent) null else it.healthVersion,
            )
        }
    }

    fun confirmSelectedService() {
        val selected = _uiState.value.pendingService ?: _uiState.value.discoveredServices.firstOrNull() ?: return
        selectService(selected)
    }

    fun createNewSession() {
        val service = _uiState.value.service ?: return appendError(tr(R.string.ov_service_not_found))
        val project = _uiState.value.selectedProject ?: return appendError(tr(R.string.ov_project_not_selected))
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, isHistoryLoading = true, errorMessage = null) }
            runCatching {
                val created = client.createSession(service.baseUrl, project)
                sessions = (listOf(created) + sessions.filterNot { it.id == created.id })
                    .sortedByDescending { it.time }
                val selected = enrichProjectWithVcs(service, project.withSession(created))
                val history = resolveHistoryMessages(service, selected)
                syncSessionTodos(service, selected)
                selected to history
            }.onSuccess { (selected, history) ->
                val restored = resolveSelectionForContext(
                    service = service,
                    project = selected,
                    models = _uiState.value.models,
                    modes = _uiState.value.availableModes,
                )
                _uiState.update { state ->
                    state.copy(
                        selectedProject = selected,
                        projects = mergeProject(state.projects, selected),
                        recentSessions = sessionsForProject(selected),
                        messages = history,
                        todos = currentTodosForProject(selected),
                        selectedMode = restored.mode,
                        selectedThinkingLevel = restored.thinkingLevel,
                        selectedModel = restored.model,
                        isConnecting = false,
                        isHistoryLoading = false,
                        discoveryStatus = tr(R.string.ov_new_session_created),
                        errorMessage = null,
                    )
                }
                persistCurrentSelection()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isHistoryLoading = false,
                        errorMessage = tr(R.string.ov_create_session_failed, throwable.toFriendlyError()),
                    )
                }
            }
        }
    }

    fun selectProject(projectDirectory: String) {
        val service = _uiState.value.service ?: return
        val normalizedDirectory = normalizeDirectory(projectDirectory)
        val project = _uiState.value.projects.firstOrNull {
            normalizeDirectory(it.directory) == normalizedDirectory
        } ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedProject = project,
                    isConnecting = true,
                    isHistoryLoading = true,
                    errorMessage = null,
                    messages = emptyList(),
                )
            }
            runCatching {
                val selected = enrichProjectWithVcs(service, project.withSession(resolveSessionForProject(service, project)))
                val history = resolveHistoryMessages(service, selected)
                syncSessionTodos(service, selected)
                selected to history
            }.onSuccess { (selected, history) ->
                val restored = resolveSelectionForContext(
                    service = service,
                    project = selected,
                    models = _uiState.value.models,
                    modes = _uiState.value.availableModes,
                )
                _uiState.update { state ->
                    state.copy(
                        selectedProject = selected,
                        projects = mergeProject(state.projects, selected),
                        recentSessions = sessionsForProject(selected),
                        messages = history,
                        todos = currentTodosForProject(selected),
                        selectedMode = restored.mode,
                        selectedThinkingLevel = restored.thinkingLevel,
                        selectedModel = restored.model,
                        isConnecting = false,
                        isHistoryLoading = false,
                        discoveryStatus = tr(R.string.ov_switched_to_project, selected.name),
                    )
                }
                persistCurrentSelection()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isHistoryLoading = false,
                        errorMessage = tr(R.string.ov_switch_project_failed, throwable.message ?: tr(R.string.ov_unknown_error)),
                    )
                }
            }
        }
    }

    fun deleteProject(projectDirectory: String) {
        val service = _uiState.value.service ?: return
        val targetDirectory = normalizeDirectory(projectDirectory)
        val state = _uiState.value
        val updatedProjects = state.projects.filterNot {
            normalizeDirectory(it.directory) == targetDirectory
        }
        val updatedLocalProjects = state.localProjects.filterNot {
            normalizeDirectory(it.directory) == targetDirectory
        }
        val nextSelected = state.selectedProject?.takeIf {
            normalizeDirectory(it.directory) != targetDirectory
        } ?: updatedProjects.firstOrNull()
        preferences.saveLocalProjects(service.key(), updatedLocalProjects)
        _uiState.update {
            it.copy(
                projects = updatedProjects,
                localProjects = updatedLocalProjects,
                selectedProject = nextSelected,
                recentSessions = sessionsForProject(nextSelected),
                messages = if (nextSelected == null) emptyList() else it.messages,
                todos = currentTodosForProject(nextSelected),
                discoveryStatus = tr(R.string.ov_project_deleted),
            )
        }
        persistCurrentSelection()
    }

    fun showAddProjectPicker() {
        val service = _uiState.value.service ?: return appendError(tr(R.string.ov_service_not_found))
        _uiState.update {
            it.copy(
                isProjectPickerVisible = true,
                isProjectPickerLoading = true,
                projectPickerErrorMessage = null,
                projectPickerCurrentDirectory = null,
                projectPickerQuery = "",
                projectPickerDirectories = emptyList(),
            )
        }
        viewModelScope.launch {
            runCatching {
                val roots = client.listPathRoots(service.baseUrl)
                val root = roots.firstOrNull() ?: error(tr(R.string.ov_no_available_directory))
                var selectedRoot = root
                var directories: List<OpenCodeDirectoryEntry> = emptyList()
                for (candidate in roots) {
                    val found = runCatching {
                        client.findDirectories(service.baseUrl, directory = candidate)
                    }.getOrElse { emptyList() }
                    if (found.isNotEmpty()) {
                        selectedRoot = candidate
                        directories = found
                        break
                    }
                }
                if (directories.isEmpty()) {
                    directories = client.findDirectories(service.baseUrl, directory = selectedRoot)
                }
                selectedRoot to directories
            }.onSuccess { (root, directories) ->
                _uiState.update {
                    it.copy(
                        isProjectPickerLoading = false,
                        projectPickerCurrentDirectory = root,
                        projectPickerQuery = "",
                        projectPickerDirectories = directories,
                        projectPickerErrorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isProjectPickerLoading = false,
                        projectPickerErrorMessage = tr(R.string.ov_load_directory_failed, throwable.toFriendlyError()),
                    )
                }
            }
        }
    }

    fun dismissAddProjectPicker() {
        projectPickerQueryJob?.cancel()
        projectPickerQueryJob = null
        _uiState.update {
            it.copy(
                isProjectPickerVisible = false,
                isProjectPickerLoading = false,
                projectPickerErrorMessage = null,
                projectPickerCurrentDirectory = null,
                projectPickerQuery = "",
                projectPickerDirectories = emptyList(),
            )
        }
    }

    fun updateProjectPickerQuery(query: String) {
        val service = _uiState.value.service ?: return
        val currentDirectory = _uiState.value.projectPickerCurrentDirectory ?: return
        _uiState.update { it.copy(projectPickerQuery = query) }
        projectPickerQueryJob?.cancel()
        projectPickerQueryJob = viewModelScope.launch {
            delay(220)
            loadProjectDirectories(
                service = service,
                directory = currentDirectory,
                query = query,
            )
        }
    }

    fun openProjectDirectory(directory: String) {
        val service = _uiState.value.service ?: return
        projectPickerQueryJob?.cancel()
        projectPickerQueryJob = null
        _uiState.update {
            it.copy(
                isProjectPickerLoading = true,
                projectPickerQuery = "",
                projectPickerErrorMessage = null,
            )
        }
        viewModelScope.launch { loadProjectDirectories(service = service, directory = directory, query = "") }
    }

    fun openProjectDirectoryParent() {
        val current = _uiState.value.projectPickerCurrentDirectory ?: return
        val trimmed = current.trimEnd('/')
        val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
            .ifBlank { "/" }
        if (parent == current) return
        openProjectDirectory(parent)
    }

    fun addProjectFromCurrentDirectory() {
        val service = _uiState.value.service ?: return appendError(tr(R.string.ov_service_not_found))
        val directory = normalizeDirectory(_uiState.value.projectPickerCurrentDirectory ?: return appendError(tr(R.string.ov_empty_directory)))
        val projectName = directory.substringAfterLast('/').ifBlank { directory }
        val localProject = OpenCodeProject(
            id = localProjectId(directory),
            name = projectName,
            directory = directory,
            isLocalOnly = true,
        )
        val state = _uiState.value
        val updatedLocal = (state.localProjects + localProject)
            .map { it.copy(directory = normalizeDirectory(it.directory), isLocalOnly = true) }
            .distinctBy { normalizeDirectory(it.directory) }
        val mergedProjects = mergeLocalProjects(state.projects.filterNot { it.isLocalOnly }, updatedLocal)
        preferences.saveLocalProjects(service.key(), updatedLocal)
        _uiState.update {
            it.copy(
                projects = mergedProjects,
                localProjects = updatedLocal,
                selectedProject = mergedProjects.firstOrNull { project -> project.directory == directory } ?: it.selectedProject,
                isProjectPickerVisible = false,
                isProjectPickerLoading = false,
                projectPickerErrorMessage = null,
                discoveryStatus = tr(R.string.ov_local_project_added, projectName),
            )
        }
        persistCurrentSelection()
    }

    private suspend fun loadProjectDirectories(
        service: OpenCodeService,
        directory: String,
        query: String,
    ) {
        runCatching {
            val dirs = client.findDirectories(
                baseUrl = service.baseUrl,
                directory = directory,
                query = query,
            )
            directory to dirs
        }.onSuccess { (current, directories) ->
            _uiState.update {
                it.copy(
                    isProjectPickerLoading = false,
                    projectPickerCurrentDirectory = current,
                    projectPickerDirectories = directories,
                    projectPickerErrorMessage = null,
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    isProjectPickerLoading = false,
                    projectPickerErrorMessage = tr(R.string.ov_open_directory_failed, throwable.toFriendlyError()),
                )
            }
        }
    }

    fun selectSession(sessionId: String) {
        val service = _uiState.value.service ?: return
        val session = sessions.firstOrNull { it.id == sessionId } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, isHistoryLoading = true, errorMessage = null) }
            runCatching {
                val baseProject = resolveProjectForSession(session)
                val selected = enrichProjectWithVcs(service, baseProject.withSession(session))
                val history = resolveHistoryMessages(service, selected)
                syncSessionTodos(service, selected)
                selected to history
            }.onSuccess { (selected, history) ->
                val updatedLocalProjects = (_uiState.value.localProjects + selected.copy(isLocalOnly = true))
                    .map { it.copy(directory = normalizeDirectory(it.directory), isLocalOnly = true) }
                    .distinctBy { normalizeDirectory(it.directory) }
                preferences.saveLocalProjects(service.key(), updatedLocalProjects)
                val restored = resolveSelectionForContext(
                    service = service,
                    project = selected,
                    models = _uiState.value.models,
                    modes = _uiState.value.availableModes,
                )
                _uiState.update { state ->
                    state.copy(
                        selectedProject = selected,
                        projects = mergeProject(state.projects, selected),
                        localProjects = updatedLocalProjects,
                        recentSessions = sessionsForProject(selected),
                        messages = history,
                        todos = currentTodosForProject(selected),
                        selectedMode = restored.mode,
                        selectedThinkingLevel = restored.thinkingLevel,
                        selectedModel = restored.model,
                        isConnecting = false,
                        isHistoryLoading = false,
                        errorMessage = null,
                        discoveryStatus = tr(R.string.ov_session_switched),
                    )
                }
                persistCurrentSelection()
            }.onFailure { throwable ->
                val friendly = throwable.toFriendlyError()
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isHistoryLoading = false,
                        errorMessage = tr(R.string.ov_switch_session_failed, friendly),
                    )
                }
            }
        }
    }

    fun selectMode(mode: OpenCodeMode) {
        _uiState.update { it.copy(selectedMode = mode) }
        persistCurrentSelection()
    }

    fun selectModel(model: OpenCodeModelOption) {
        _uiState.update { it.copy(selectedModel = model) }
        persistCurrentSelection()
    }

    fun selectThinking(level: ThinkingLevel) {
        _uiState.update { it.copy(selectedThinkingLevel = level) }
        persistCurrentSelection()
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank()) return
        if (_uiState.value.isSending) return
        if (prompt.matches(Regex("^/new\\s*$"))) {
            createNewSession()
            return
        }

        val state = _uiState.value
        val service = state.service ?: return appendError(tr(R.string.ov_service_not_found))
        val project = state.selectedProject ?: return appendError(tr(R.string.ov_project_not_selected))

        val userMessage = OpenCodeChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = prompt,
        )
        val assistantPlaceholder = OpenCodeChatMessage(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = "",
            reasoningCompleted = false,
        )
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + assistantPlaceholder,
                isSending = true,
                errorMessage = null,
            )
        }
        project.sessionId?.let { sessionId ->
            preferences.saveSessionMessages(service.key(), sessionId, _uiState.value.messages)
        }

        sendJob = viewModelScope.launch {
            try {
                runCatching {
                val session = resolveSessionForProject(service, project)
                sendingSessionId = session.id
                val selectedProject = project.withSession(session)
                _uiState.update { current ->
                    current.copy(
                        selectedProject = selectedProject,
                        projects = mergeProject(current.projects, selectedProject),
                    )
                }
                preferences.saveSessionMessages(service.key(), session.id, _uiState.value.messages)
                persistCurrentSelection()

                val selectedModel = _uiState.value.selectedModel
                Log.i(
                    "OpenCodeStream",
                    "ui:send mode=${_uiState.value.selectedMode.agent} thinking=${_uiState.value.selectedThinkingLevel.name} " +
                        "model={providerID=${selectedModel.providerID}, modelID=${selectedModel.modelID}, label=${selectedModel.label}}",
                )
                val assistantMessage = if (prompt.startsWith("/")) {
                    val segments = prompt.split(Regex("\\s+")).filter { it.isNotBlank() }
                    val command = segments.first().removePrefix("/")
                    client.executeCommand(
                        baseUrl = service.baseUrl,
                        sessionId = session.id,
                        command = command,
                        arguments = segments.drop(1),
                        messageId = null,
                        agent = _uiState.value.selectedMode,
                        model = _uiState.value.selectedModel,
                        directory = selectedProject.directory,
                    )
                } else {
                    client.sendMessage(
                        baseUrl = service.baseUrl,
                        sessionId = session.id,
                        prompt = prompt,
                        mode = _uiState.value.selectedMode,
                        model = _uiState.value.selectedModel,
                        thinkingLevel = _uiState.value.selectedThinkingLevel,
                        directory = selectedProject.directory,
                        onPermissionRequested = { permission ->
                            _uiState.update { current -> current.copy(pendingPermission = permission) }
                        },
                        onTodoUpdated = { todos ->
                            sessionTodoCache[session.id] = todos
                            _uiState.update { current ->
                                val currentSessionId = current.selectedProject?.sessionId
                                if (currentSessionId == session.id) {
                                    current.copy(todos = todos)
                                } else {
                                    current
                                }
                            }
                        },
                        onDelta = { delta ->
                            if (delta.isBlank()) return@sendMessage
                            Log.i("OpenCodeStream", "ui:delta len=${delta.length} preview=${delta.take(60)}")
                            _uiState.update { current ->
                                current.copy(
                                    messages = current.messages.map { message ->
                                        if (message.id == assistantPlaceholder.id) {
                                            message.copy(
                                                content = message.content + delta,
                                            )
                                        } else {
                                            message
                                        }
                                    },
                                )
                            }
                            preferences.saveSessionMessages(service.key(), session.id, _uiState.value.messages)
                        },
                        onReasoningUpdated = { reasoning ->
                            _uiState.update { current ->
                                current.copy(
                                    messages = current.messages.map { message ->
                                        if (message.id == assistantPlaceholder.id) {
                                            message.copy(
                                                reasoningContent = reasoning,
                                                reasoningCompleted = false,
                                            )
                                        } else {
                                            message
                                        }
                                    },
                                )
                            }
                            preferences.saveSessionMessages(service.key(), session.id, _uiState.value.messages)
                        },
                    )
                }
                assistantMessage to session.id
                }.onSuccess { (assistantMessage, sessionId) ->
                    Log.i("OpenCodeStream", "ui:send.success sessionId=$sessionId assistantId=${assistantMessage.id} contentLen=${assistantMessage.content.length}")
                    _uiState.update {
                        it.copy(
                            messages = it.messages.map { message ->
                                if (message.id == assistantPlaceholder.id) {
                                    message.copy(
                                        id = assistantMessage.id.ifBlank { assistantPlaceholder.id },
                                        content = assistantMessage.content.ifBlank { message.content },
                                        reasoningContent = assistantMessage.reasoningContent.ifBlank { message.reasoningContent },
                                        reasoningCompleted = true,
                                        role = "assistant",
                                    )
                                } else {
                                    message
                                }
                            },
                            isSending = false,
                            pendingPermission = null,
                            errorMessage = null,
                            recentSessions = bumpSessionInRecent(it.recentSessions, sessionId),
                        )
                    }
                    sendingSessionId = null
                    sendJob = null
                    persistCurrentSelection()
                }.onFailure { throwable ->
                    Log.e("OpenCodeStream", "ui:send.failure message=${throwable.message}", throwable)
                    if (throwable.isAbortError()) {
                        _uiState.update {
                            it.copy(
                                messages = it.messages.map { message ->
                                    if (message.id == assistantPlaceholder.id) {
                                        message.copy(content = tr(R.string.ov_terminated), isError = false)
                                    } else {
                                        message
                                    }
                                },
                                isSending = false,
                                pendingPermission = null,
                                errorMessage = null,
                            )
                        }
                        sendingSessionId = null
                        sendJob = null
                        return@onFailure
                    }
                    val friendly = throwable.toFriendlyError()
                    _uiState.update {
                        it.copy(
                            messages = it.messages.map { message ->
                                if (message.id == assistantPlaceholder.id) {
                                    message.copy(content = tr(R.string.ov_send_failed, friendly), isError = true)
                                } else {
                                    message
                                }
                            },
                            isSending = false,
                            pendingPermission = null,
                            errorMessage = friendly,
                        )
                    }
                    sendingSessionId = null
                    sendJob = null
                }
            } finally {
                Log.i(
                    "OpenCodeStream",
                    "ui:send.finally wasSending=${_uiState.value.isSending} sessionId=${sendingSessionId.orEmpty()}",
                )
                sendingSessionId = null
                sendJob = null
                _uiState.update { current ->
                    if (current.isSending) current.copy(isSending = false) else current
                }
            }
        }
    }

    fun abortSending() {
        if (!_uiState.value.isSending) return
        val service = _uiState.value.service ?: return
        val sessionId = sendingSessionId ?: _uiState.value.selectedProject?.sessionId ?: return
        viewModelScope.launch {
            runCatching {
                client.abortSession(service.baseUrl, sessionId, _uiState.value.selectedProject?.directory)
            }.onSuccess {
                sendJob?.cancel()
                _uiState.update {
                    it.copy(
                        isSending = false,
                        pendingPermission = null,
                        errorMessage = null,
                    )
                }
                sendingSessionId = null
                sendJob = null
            }.onFailure { throwable ->
                appendError(tr(R.string.ov_terminate_session_failed, throwable.toFriendlyError()))
                sendJob?.cancel()
                _uiState.update { it.copy(isSending = false) }
                sendingSessionId = null
                sendJob = null
            }
        }
    }

    fun respondPermission(response: OpenCodePermissionResponse) {
        val state = _uiState.value
        val service = state.service ?: return
        val pending = state.pendingPermission ?: return
        if (state.isRespondingPermission) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRespondingPermission = true, errorMessage = null) }
            runCatching {
                client.replyPermission(
                    baseUrl = service.baseUrl,
                    sessionId = pending.sessionId,
                    permissionId = pending.permissionId,
                    response = response,
                    directory = state.selectedProject?.directory,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        pendingPermission = null,
                        isRespondingPermission = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isRespondingPermission = false,
                        errorMessage = tr(R.string.ov_permission_response_failed, throwable.toFriendlyError()),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        discovery.stop()
        super.onCleared()
    }

    private fun observeDiscovery() {
        if (discoveryObserverStarted) return
        discoveryObserverStarted = true
        viewModelScope.launch {
            discovery.state.collectLatest { state ->
                when (state) {
                    OpenCodeDiscoveryState.Idle -> _uiState.update {
                        it.copy(discoveryStatus = tr(R.string.ov_search_not_started), isConnecting = false)
                    }

                    OpenCodeDiscoveryState.Searching -> _uiState.update {
                        it.copy(
                            discoveryStatus = tr(R.string.ov_searching_mdns),
                            isConnecting = true,
                            errorMessage = null,
                        )
                    }

                    is OpenCodeDiscoveryState.Found -> onServiceDiscovered(state.service)

                    is OpenCodeDiscoveryState.Error -> _uiState.update {
                        it.copy(
                            discoveryStatus = state.message,
                            isConnecting = false,
                            errorMessage = state.message,
                        )
                    }
                }
            }
        }
    }

    private fun onServiceDiscovered(service: OpenCodeService) {
        val current = _uiState.value
        val services = (current.discoveredServices + service).distinctBy { it.key() }

        _uiState.update { it.copy(discoveredServices = services) }

        val preferredServiceKey = preferences.getSelectedServiceKey()
        val preferred = services.firstOrNull { it.key() == preferredServiceKey }
        if (preferred != null) {
            if (current.service?.key() != preferred.key()) {
                connectToService(preferred)
            }
            return
        }

        if (current.service != null) return

        if (services.size == 1) {
            connectToService(services.first())
            return
        }

        _uiState.update {
            it.copy(
                pendingServiceSelection = true,
                pendingService = it.pendingService ?: services.first(),
                discoveryStatus = tr(R.string.ov_found_multiple_services_select_first),
                isConnecting = false,
            )
        }
    }

    private fun connectToService(service: OpenCodeService) {
        if (_uiState.value.service?.key() == service.key() && _uiState.value.projects.isNotEmpty()) return

        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    service = service,
                    discoveredServices = (it.discoveredServices + service).distinctBy { s -> s.key() },
                    discoveryStatus = tr(R.string.ov_found_service_connecting, service.serviceName),
                    pendingServiceSelection = false,
                    pendingService = null,
                    isConnecting = true,
                    isHistoryLoading = true,
                    errorMessage = null,
                )
            }

            runCatching {
                client.setBasicAuth(service.username, service.password)
                val health = client.health(service.baseUrl)
                if (!health.healthy) error(tr(R.string.ov_health_check_failed))
                sessions = client.listSessions(service.baseUrl)
                val sessionProjects = OpenCodeProject.fromSessions(sessions).map { it.copy(isLocalOnly = true) }
                val savedLocalProjects = preferences.getLocalProjects(service.key())
                val localProjects = (savedLocalProjects + sessionProjects)
                    .map { it.copy(isLocalOnly = true, directory = normalizeDirectory(it.directory)) }
                    .distinctBy { normalizeDirectory(it.directory) }
                val projects = localProjects
                val selectedBase = chooseSelectedProject(service, projects)
                val selectedWithSession = selectedBase?.withSession(
                    selectedBase.let { resolveSessionForProject(service, it) },
                )
                val selectedWithVcs = selectedWithSession?.let { enrichProjectWithVcs(service, it) }
                sessionTodoCache.keys.retainAll(sessions.map { it.id }.toSet())
                val models = client.listModels(service.baseUrl)
                val modes = client.listAgents(service.baseUrl)
                val history = resolveHistoryMessages(service, selectedWithVcs)
                syncSessionTodos(service, selectedWithVcs)
                val selection = resolveSelectionForContext(service, selectedWithVcs, models, modes)
                SessionConnectData(
                    projects = projects,
                    localProjects = localProjects,
                    selectedProject = selectedWithVcs,
                    models = models,
                    modes = modes,
                    messages = history,
                    healthVersion = health.version,
                    selection = selection,
                )
            }.onSuccess { data ->
                preferences.saveSelectedServiceKey(service.key())
                preferences.saveLocalProjects(service.key(), data.localProjects)
                _uiState.update {
                    val selected = data.selectedProject
                    val mergedProjects = data.selectedProject?.let { selected ->
                        mergeProject(data.projects, selected)
                    } ?: data.projects
                    it.copy(
                        projects = mergedProjects,
                        localProjects = data.localProjects,
                        selectedProject = selected,
                        recentSessions = sessionsForProject(selected),
                        messages = data.messages,
                        todos = currentTodosForProject(selected),
                        availableModes = data.modes,
                        selectedMode = data.selection.mode,
                        models = data.models,
                        selectedModel = data.selection.model,
                        selectedThinkingLevel = data.selection.thinkingLevel,
                        healthVersion = data.healthVersion,
                        discoveryStatus = tr(R.string.ov_connected_service, service.serviceName),
                        isConnecting = false,
                        isHistoryLoading = false,
                        errorMessage = null,
                    )
                }
                persistCurrentSelection()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        discoveryStatus = tr(R.string.ov_service_connect_failed),
                        isConnecting = false,
                        isHistoryLoading = false,
                        errorMessage = throwable.toFriendlyError(),
                    )
                }
            }
        }
    }

    private fun chooseSelectedProject(
        service: OpenCodeService,
        projects: List<OpenCodeProject>,
    ): OpenCodeProject? {
        if (projects.isEmpty()) return null

        val serviceKey = service.key()
        val savedProjectDirectory = preferences.getSelectedProjectDirectory(serviceKey)
        val savedProjectId = preferences.getSelectedProjectId(serviceKey)
        val savedSessionId = preferences.getSelectedSessionId(serviceKey)

        val base = when {
            !savedProjectDirectory.isNullOrBlank() -> projects.firstOrNull {
                normalizeDirectory(it.directory) == normalizeDirectory(savedProjectDirectory)
            }
            !savedProjectId.isNullOrBlank() -> projects.firstOrNull { it.id == savedProjectId }
            else -> projects.firstOrNull()
        } ?: return null

        if (savedSessionId.isNullOrBlank()) return base

        val savedSession = sessions.firstOrNull { it.id == savedSessionId } ?: return base
        val matched = sessionMatchesProject(savedSession, base)
        return if (matched) base.copy(sessionId = savedSession.id) else base
    }

    private fun sessionMatchesProject(session: OpenCodeSession, project: OpenCodeProject): Boolean {
        val sessionDirectory = session.directory?.let(::normalizeDirectory)
        val projectDirectory = normalizeDirectory(project.directory)
        return session.projectID == project.id || sessionDirectory == projectDirectory
    }

    private suspend fun resolveHistoryMessages(
        service: OpenCodeService,
        project: OpenCodeProject?,
    ): List<OpenCodeChatMessage> {
        val sessionId = project?.sessionId ?: return emptyList()
        val serviceKey = service.key()
        val localHistory = preferences.getSessionMessages(serviceKey, sessionId)
        return runCatching { client.listSessionMessages(service.baseUrl, sessionId, project.directory) }
            .onSuccess { remote ->
                if (remote.isNotEmpty()) {
                    preferences.saveSessionMessages(serviceKey, sessionId, remote)
                }
            }
            .getOrElse { emptyList() }
            .ifEmpty { localHistory }
    }

    private suspend fun syncSessionTodos(
        service: OpenCodeService,
        project: OpenCodeProject?,
    ) {
        val sessionId = project?.sessionId ?: return
        val todos = runCatching {
            client.listSessionTodos(service.baseUrl, sessionId, project.directory)
        }.getOrElse { emptyList() }
        sessionTodoCache[sessionId] = todos
    }

    private suspend fun enrichProjectWithVcs(
        service: OpenCodeService,
        project: OpenCodeProject,
    ): OpenCodeProject {
        val vcs = runCatching { client.getVcsInfo(service.baseUrl, project.directory) }.getOrNull() ?: return project
        val statusSummary = runCatching { client.getGitStatusSummary(service.baseUrl, project.directory) }.getOrNull()
        return project.copy(
            gitBranch = vcs.branch ?: project.gitBranch,
            gitStatusSummary = statusSummary ?: vcs.statusSummary ?: project.gitStatusSummary,
        )
    }

    private suspend fun resolveSessionForProject(service: OpenCodeService, project: OpenCodeProject): OpenCodeSession {
        val projectDirectory = normalizeDirectory(project.directory)
        project.sessionId?.let { sessionId ->
            sessions.firstOrNull { it.id == sessionId }?.let { session ->
                val sessionDirectory = session.directory?.let(::normalizeDirectory)
                if (sessionDirectory == projectDirectory) {
                    return session
                }
            }
        }
        sessions
            .asSequence()
            .filter { session ->
                val sessionDirectory = session.directory?.let(::normalizeDirectory)
                sessionDirectory == projectDirectory
            }
            .maxByOrNull { it.time }
            ?.let { return it }
        sessions.firstOrNull { session ->
            session.projectID == project.id
        }?.let { return it }
        val created = client.createSession(service.baseUrl, project)
        sessions = sessions + created
        return created
    }

    private fun resolveProjectForSession(session: OpenCodeSession): OpenCodeProject {
        val directory = session.directory ?: "/"
        val normalizedDirectory = normalizeDirectory(directory)
        val normalizedName = directory.substringAfterLast('/').ifBlank { directory }.uppercase()
        val existing = _uiState.value.projects.firstOrNull { project ->
            normalizeDirectory(project.directory) == normalizedDirectory
        }
        if (existing != null) {
            return existing.copy(
                name = normalizedName,
                sessionId = session.id,
                isLocalOnly = true,
            )
        }
        return OpenCodeProject(
            id = localProjectId(normalizedDirectory),
            name = normalizedName,
            directory = normalizedDirectory,
            sessionId = session.id,
            isLocalOnly = true,
        )
    }

    private fun mergeProject(projects: List<OpenCodeProject>, selected: OpenCodeProject): List<OpenCodeProject> {
        val selectedDirectory = normalizeDirectory(selected.directory)
        val hasExisting = projects.any { normalizeDirectory(it.directory) == selectedDirectory }
        return if (hasExisting) {
            projects.map { project ->
                if (normalizeDirectory(project.directory) == selectedDirectory) {
                    selected.copy(directory = selectedDirectory)
                } else {
                    project.copy(directory = normalizeDirectory(project.directory))
                }
            }.distinctBy { normalizeDirectory(it.directory) }
        } else {
            (listOf(selected.copy(directory = selectedDirectory)) + projects.map {
                it.copy(directory = normalizeDirectory(it.directory))
            }).distinctBy { normalizeDirectory(it.directory) }
        }
    }

    private fun mergeLocalProjects(
        serverProjects: List<OpenCodeProject>,
        localProjects: List<OpenCodeProject>,
    ): List<OpenCodeProject> {
        val normalizedServer = serverProjects.map { it.copy(directory = normalizeDirectory(it.directory)) }
        val normalizedLocal = localProjects
            .map { it.copy(isLocalOnly = true, directory = normalizeDirectory(it.directory)) }
            .distinctBy { normalizeDirectory(it.directory) }
        val missingLocal = normalizedLocal.filter { local ->
            normalizedServer.none { server ->
                normalizeDirectory(server.directory) == normalizeDirectory(local.directory)
            }
        }
        return (normalizedServer + missingLocal)
            .distinctBy { normalizeDirectory(it.directory) }
    }

    private fun localProjectId(directory: String): String = "local::$directory"

    private fun normalizeDirectory(directory: String): String {
        if (directory == "/") return directory
        return directory.trimEnd('/').ifBlank { "/" }
    }

    private fun bumpSessionInRecent(recent: List<OpenCodeSession>, sessionId: String): List<OpenCodeSession> {
        val now = System.currentTimeMillis()
        val target = recent.firstOrNull { it.id == sessionId } ?: sessions.firstOrNull { it.id == sessionId }
        if (target == null) return recent
        val updated = target.copy(time = now)
        sessions = (listOf(updated) + sessions.filterNot { it.id == sessionId }).distinctBy { it.id }
        return sessionsForProject(_uiState.value.selectedProject)
    }

    private fun sessionsForProject(project: OpenCodeProject?): List<OpenCodeSession> {
        val selected = project ?: return emptyList()
        val selectedDirectory = normalizeDirectory(selected.directory)
        return sessions
            .filter { session ->
                val sessionDirectory = session.directory?.let(::normalizeDirectory)
                sessionDirectory == selectedDirectory
            }
            .sortedByDescending { it.time }
    }

    private fun currentTodosForProject(project: OpenCodeProject?): List<OpenCodeTodoItem> {
        val sessionId = project?.sessionId ?: return emptyList()
        return sessionTodoCache[sessionId].orEmpty()
    }

    private fun persistCurrentSelection() {
        val state = _uiState.value
        val service = state.service ?: return
        val serviceKey = service.key()

        preferences.saveSelectedServiceKey(serviceKey)
        preferences.saveSelectedProjectId(serviceKey, state.selectedProject?.id)
        preferences.saveSelectedProjectDirectory(serviceKey, state.selectedProject?.directory?.let(::normalizeDirectory))
        preferences.saveSelectedSessionId(serviceKey, state.selectedProject?.sessionId)

        val selectedProject = state.selectedProject ?: return
        preferences.saveSessionSelection(
            serviceKey = serviceKey,
            projectId = selectedProject.id,
            sessionId = selectedProject.sessionId,
            mode = state.selectedMode,
            thinkingLevel = state.selectedThinkingLevel,
            modelProviderID = state.selectedModel.providerID,
            modelID = state.selectedModel.modelID,
        )
        selectedProject.sessionId?.let { sessionId ->
            preferences.saveSessionMessages(serviceKey, sessionId, state.messages)
        }
    }

    private fun resolveSelectionForContext(
        service: OpenCodeService,
        project: OpenCodeProject?,
        models: List<OpenCodeModelOption>,
        modes: List<OpenCodeMode>,
    ): SelectionState {
        val selection = preferences.getSessionSelection(service.key(), project?.id, project?.sessionId)

        val mode = selection.modeAgent?.let { savedAgent ->
            modes.firstOrNull { it.agent.equals(savedAgent, ignoreCase = true) }
        }
            ?: modes.firstOrNull { it.agent.equals("build", ignoreCase = true) }
            ?: modes.firstOrNull()
            ?: OpenCodeMode.defaultBuild

        val thinking = selection.thinkingLevel ?: ThinkingLevel.DEFAULT

        val model = if (!selection.modelProviderID.isNullOrBlank() && !selection.modelID.isNullOrBlank()) {
            models.firstOrNull { candidate ->
                candidate.providerID == selection.modelProviderID && candidate.modelID == selection.modelID
            }
        } else {
            null
        } ?: selectInitialModel(models)

        return SelectionState(mode = mode, model = model, thinkingLevel = thinking)
    }

    private fun selectInitialModel(models: List<OpenCodeModelOption>): OpenCodeModelOption {
        return models.firstOrNull { it.label.contains("gemini", ignoreCase = true) }
            ?: models.firstOrNull()
            ?: OpenCodeModelOption.defaults.first()
    }

    private fun appendError(message: String) {
        _uiState.update {
            it.copy(
                messages = it.messages + OpenCodeChatMessage(
                    role = "assistant",
                    content = message,
                    isError = true,
                ),
                errorMessage = message,
            )
        }
    }

    private fun Throwable.toFriendlyError(): String {
        return when (this) {
            is SocketTimeoutException -> tr(R.string.ov_error_timeout)
            is InterruptedIOException -> tr(R.string.ov_error_interrupted)
            is ConnectException -> tr(R.string.ov_error_connect_failed)
            is UnknownHostException -> tr(R.string.ov_error_unknown_host)
            else -> message ?: tr(R.string.ov_unknown_error)
        }
    }

    private fun Throwable.isAbortError(): Boolean {
        val msg = message.orEmpty()
        return msg.equals("Aborted", ignoreCase = true) ||
            msg.contains("aborted", ignoreCase = true) ||
            (this is InterruptedIOException && msg.contains("canceled", ignoreCase = true))
    }

    private fun OpenCodeService.key(): String = "$host:$port"

    private data class SelectionState(
        val mode: OpenCodeMode,
        val model: OpenCodeModelOption,
        val thinkingLevel: ThinkingLevel,
    )

    private data class SessionConnectData(
        val projects: List<OpenCodeProject>,
        val localProjects: List<OpenCodeProject>,
        val selectedProject: OpenCodeProject?,
        val models: List<OpenCodeModelOption>,
        val modes: List<OpenCodeMode>,
        val messages: List<OpenCodeChatMessage>,
        val healthVersion: String?,
        val selection: SelectionState,
    )
}
