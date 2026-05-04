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

package com.github.go_kenka.opencode.opencode.api

import android.util.Log
import com.github.go_kenka.opencode.opencode.model.OpenCodeChatMessage
import com.github.go_kenka.opencode.opencode.model.OpenCodeDirectoryEntry
import com.github.go_kenka.opencode.opencode.model.OpenCodeHealth
import com.github.go_kenka.opencode.opencode.model.OpenCodeMode
import com.github.go_kenka.opencode.opencode.model.OpenCodeModelOption
import com.github.go_kenka.opencode.opencode.model.OpenCodePermissionRequest
import com.github.go_kenka.opencode.opencode.model.OpenCodePermissionResponse
import com.github.go_kenka.opencode.opencode.model.OpenCodeProject
import com.github.go_kenka.opencode.opencode.model.OpenCodeSession
import com.github.go_kenka.opencode.opencode.model.OpenCodeTodoItem
import com.github.go_kenka.opencode.opencode.model.OpenCodeVcsInfo
import com.github.go_kenka.opencode.opencode.model.ThinkingLevel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenCodeClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(190, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    @Volatile
    private var basicAuthHeader: String? = null

    private val eventClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun setBasicAuth(username: String?, password: String?) {
        basicAuthHeader = if (username.isNullOrBlank()) {
            null
        } else {
            Credentials.basic(username, password.orEmpty())
        }
    }

    suspend fun health(baseUrl: String): OpenCodeHealth = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .applyAuth()
            .url(baseUrl.endpoint("global/health"))
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull() ?: JSONObject()
            OpenCodeHealth(
                healthy = json.optBoolean("healthy", false),
                version = json.optString("version").takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun listPathRoots(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .applyAuth()
            .url(baseUrl.endpoint("path"))
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
            OpenCodeJson.parsePathDirectories(response.body?.string().orEmpty())
        }
    }

    suspend fun findDirectories(
        baseUrl: String,
        directory: String,
        query: String = "",
        limit: Int = 50,
    ): List<OpenCodeDirectoryEntry> = withContext(Dispatchers.IO) {
        val url = baseUrl.endpoint("find/file").toHttpUrl().newBuilder()
            .addQueryParameter("directory", directory)
            .addQueryParameter("query", query)
            .addQueryParameter("type", "directory")
            .addQueryParameter("limit", limit.toString())
            .build()
        val request = Request.Builder()
            .applyAuth()
            .url(url)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
            OpenCodeJson.parseDirectoryEntries(
                json = response.body?.string().orEmpty(),
                baseDirectory = directory,
            )
        }
    }

    suspend fun listModels(baseUrl: String): List<OpenCodeModelOption> = withContext(Dispatchers.IO) {
        val configJson = runCatching { getBody(baseUrl, "config/providers") }.getOrNull()
        val configModels = configJson?.let(OpenCodeJson::parseModels).orEmpty()
        if (configModels.isNotEmpty()) configModels else OpenCodeModelOption.defaults
    }

    suspend fun listAgents(baseUrl: String): List<OpenCodeMode> = withContext(Dispatchers.IO) {
        val json = runCatching { getBody(baseUrl, "agent") }.getOrNull().orEmpty()
        val agents = OpenCodeJson.parseAgents(json)
        if (agents.isNotEmpty()) agents else OpenCodeMode.defaults
    }

    suspend fun listSessions(baseUrl: String): List<OpenCodeSession> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .applyAuth()
            .url(baseUrl.endpoint("session"))
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
            OpenCodeJson.parseSessions(response.body?.string().orEmpty())
        }
    }

    suspend fun listCommands(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .applyAuth()
            .url(baseUrl.endpoint("command"))
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
            val body = response.body?.string().orEmpty()
            runCatching {
                val array = JSONArray(body)
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)
                        ?.optString("name")
                        ?.takeIf { it.isNotBlank() }
                }
            }.getOrElse { emptyList() }
        }
    }

    suspend fun createSession(baseUrl: String, project: OpenCodeProject? = null): OpenCodeSession = withContext(Dispatchers.IO) {
        val url = baseUrl.endpoint("session").toHttpUrl().newBuilder().apply {
            project?.directory?.let { addQueryParameter("directory", it) }
        }.build()
        val request = requestWithDirectory(
            Request.Builder(),
            project?.directory,
        )
            .url(url)
            .post(OpenCodeJson.buildCreateSessionJson(project?.name).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
            OpenCodeJson.parseSession(response.body?.string().orEmpty())
        }
    }

    suspend fun listSessionMessages(
        baseUrl: String,
        sessionId: String,
        directory: String? = null,
    ): List<OpenCodeChatMessage> = withContext(Dispatchers.IO) {
        val json = getBody(baseUrl, "session/$sessionId/message", directory)
        OpenCodeJson.parseMessages(json)
    }

    suspend fun listSessionTodos(
        baseUrl: String,
        sessionId: String,
        directory: String? = null,
    ): List<OpenCodeTodoItem> = withContext(Dispatchers.IO) {
        val json = getBody(baseUrl, "session/$sessionId/todo", directory)
        parseTodoArray(runCatching { JSONArray(json) }.getOrElse { JSONArray() })
    }

    suspend fun getVcsInfo(baseUrl: String, directory: String? = null): OpenCodeVcsInfo = withContext(Dispatchers.IO) {
        val json = getBody(baseUrl, "vcs", directory)
        OpenCodeJson.parseVcsInfo(json)
    }

    suspend fun getGitStatusSummary(baseUrl: String, directory: String? = null): String? = withContext(Dispatchers.IO) {
        val json = getBody(baseUrl, "file/status", directory)
        OpenCodeJson.parseFileStatusSummary(json)
    }

    suspend fun sendMessage(
        baseUrl: String,
        sessionId: String,
        prompt: String,
        mode: OpenCodeMode,
        model: OpenCodeModelOption?,
        thinkingLevel: ThinkingLevel,
        directory: String? = null,
        onDelta: ((String) -> Unit)? = null,
        onReasoningUpdated: ((String) -> Unit)? = null,
        onPermissionRequested: ((OpenCodePermissionRequest) -> Unit)? = null,
        onTodoUpdated: ((List<OpenCodeTodoItem>) -> Unit)? = null,
    ): OpenCodeChatMessage = withContext(Dispatchers.IO) {
        withTimeout(STREAM_TIMEOUT_MS) {
            Log.i(TAG, "stream:start sessionId=$sessionId url=${baseUrl.endpoint("global/event")}")
            val eventRequest = requestWithDirectory(
                Request.Builder(),
                directory,
            )
                .url(baseUrl.endpoint("global/event"))
                .get()
                .build()
            eventClient.newCall(eventRequest).execute().use { response ->
                response.requireSuccessful()
                Log.i(TAG, "stream:connected sessionId=$sessionId")
                postPromptAsync(baseUrl, sessionId, prompt, mode, model, thinkingLevel, directory)
                consumeAssistantFromEventStream(
                    responseBody = response.body?.charStream() ?: error("Empty SSE body"),
                    sessionId = sessionId,
                    onDelta = onDelta,
                    onReasoningUpdated = onReasoningUpdated,
                    onPermissionRequested = onPermissionRequested,
                    onTodoUpdated = onTodoUpdated,
                )
            }
        }
    }

    suspend fun replyPermission(
        baseUrl: String,
        sessionId: String,
        permissionId: String,
        response: OpenCodePermissionResponse,
        directory: String? = null,
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("response", response.wireValue)
            .toString()
        val request = requestWithDirectory(
            Request.Builder(),
            directory,
        )
            .url(baseUrl.endpoint("session/$sessionId/permissions/$permissionId"))
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        okHttpClient.newCall(request).execute().use { httpResponse ->
            httpResponse.requireSuccessful()
        }
    }

    suspend fun abortSession(baseUrl: String, sessionId: String, directory: String? = null) = withContext(Dispatchers.IO) {
        val request = requestWithDirectory(
            Request.Builder(),
            directory,
        )
            .url(baseUrl.endpoint("session/$sessionId/abort"))
            .post("".toRequestBody(null))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
        }
    }

    suspend fun executeCommand(
        baseUrl: String,
        sessionId: String,
        command: String,
        arguments: List<String>,
        messageId: String?,
        agent: OpenCodeMode?,
        model: OpenCodeModelOption?,
        directory: String? = null,
    ): OpenCodeChatMessage = withContext(Dispatchers.IO) {
        val requestBody = OpenCodeJson.buildCommandRequestJson(
            command = command,
            arguments = arguments,
            messageId = messageId,
            agent = agent?.agent,
            model = model,
        )
        postCommand(baseUrl, sessionId, requestBody, directory)
    }

    private fun postCommand(
        baseUrl: String,
        sessionId: String,
        requestBody: String,
        directory: String? = null,
    ): OpenCodeChatMessage {
        val request = requestWithDirectory(
            Request.Builder(),
            directory,
        )
            .url(baseUrl.endpoint("session/$sessionId/command"))
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
            return OpenCodeJson.parseMessageResponse(response.body?.string().orEmpty())
        }
    }

    private fun postPromptAsync(
        baseUrl: String,
        sessionId: String,
        prompt: String,
        mode: OpenCodeMode,
        model: OpenCodeModelOption?,
        thinkingLevel: ThinkingLevel,
        directory: String? = null,
    ) {
        val requestBody = OpenCodeJson.buildMessageRequest(
            prompt = prompt,
            mode = mode,
            model = model,
            thinkingLevel = thinkingLevel,
        )
        val modelDesc = if (model == null) {
            "null"
        } else {
            "providerID=${model.providerID}, modelID=${model.modelID}, label=${model.label}"
        }
        val request = requestWithDirectory(
            Request.Builder(),
            directory,
        )
            .url(baseUrl.endpoint("session/$sessionId/prompt_async"))
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        Log.i(
            TAG,
            "prompt_async:post sessionId=$sessionId mode=${mode.agent} thinking=${thinkingLevel.name} model={$modelDesc}",
        )
        Log.i(TAG, "prompt_async:payload sessionId=$sessionId body=${requestBody.take(400)}")
        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
            Log.i(TAG, "prompt_async:accepted sessionId=$sessionId code=${response.code}")
        }
    }

    private fun consumeAssistantFromEventStream(
        responseBody: java.io.Reader,
        sessionId: String,
        onDelta: ((String) -> Unit)?,
        onReasoningUpdated: ((String) -> Unit)?,
        onPermissionRequested: ((OpenCodePermissionRequest) -> Unit)?,
        onTodoUpdated: ((List<OpenCodeTodoItem>) -> Unit)?,
    ): OpenCodeChatMessage {
        val dataBuffer = StringBuilder()
        val fullText = StringBuilder()
        val fullReasoning = StringBuilder()
        val recentEvents = ArrayDeque<String>()
        val assistantPartTexts = linkedMapOf<String, String>()
        val reasoningPartTexts = linkedMapOf<String, String>()
        val assistantPartKinds = linkedMapOf<String, String>()
        var assistantMessageId: String? = null
        var done = false
        var doneReason = "unknown"
        responseBody.useLines { lines ->
            val iterator = lines.iterator()
            while (iterator.hasNext()) {
                if (done) break
                val line = iterator.next()
                when {
                    line.startsWith("data:") -> {
                        dataBuffer.append(line.removePrefix("data:").trimStart()).append('\n')
                    }
                    line.isBlank() -> {
                        if (dataBuffer.isNotEmpty()) {
                            val eventData = dataBuffer.toString().trim()
                            val result = handleEventData(
                                eventData = eventData,
                                sessionId = sessionId,
                                currentAssistantMessageId = assistantMessageId,
                                currentText = fullText.toString(),
                            )
                            val eventType = runCatching {
                                val root = JSONObject(eventData)
                                val payload = root.optJSONObject("payload") ?: root
                                payload.optString("type").ifBlank { "unknown" }
                            }.getOrDefault("invalid_json")
                            val eventSummary = "type=$eventType completed=${result.isCompleted} error=${result.errorMessage != null} delta=${result.delta.length}"
                            recentEvents.addLast(eventSummary)
                            if (recentEvents.size > 20) recentEvents.removeFirst()
                            Log.i(TAG, "stream:event.summary sessionId=$sessionId $eventSummary")
                            if (result.newAssistantMessageId != null) {
                                if (assistantMessageId != null && assistantMessageId != result.newAssistantMessageId) {
                                    assistantPartTexts.clear()
                                    reasoningPartTexts.clear()
                                    assistantPartKinds.clear()
                                    fullText.clear()
                                    fullReasoning.clear()
                                }
                                assistantMessageId = result.newAssistantMessageId
                            }
                            if (result.partSnapshot != null && assistantMessageId != null && assistantMessageId == result.partSnapshot.messageId) {
                                assistantPartKinds[result.partSnapshot.partId] = result.partSnapshot.kind
                                if (result.partSnapshot.kind == "text") {
                                    assistantPartTexts[result.partSnapshot.partId] = result.partSnapshot.text
                                    val snapshotText = assistantPartTexts.values.joinToString("")
                                    val deltaFromSnapshot = deriveDeltaFromSnapshot(fullText.toString(), snapshotText)
                                    if (deltaFromSnapshot.isNotEmpty()) {
                                        fullText.append(deltaFromSnapshot)
                                        onDelta?.invoke(deltaFromSnapshot)
                                    } else if (snapshotText != fullText.toString()) {
                                        fullText.clear()
                                        fullText.append(snapshotText)
                                    }
                                } else if (result.partSnapshot.kind == "reasoning") {
                                    reasoningPartTexts[result.partSnapshot.partId] = result.partSnapshot.text
                                    val snapshotReasoning = reasoningPartTexts.values.joinToString("")
                                    if (snapshotReasoning != fullReasoning.toString()) {
                                        fullReasoning.clear()
                                        fullReasoning.append(snapshotReasoning)
                                        onReasoningUpdated?.invoke(fullReasoning.toString())
                                    }
                                }
                            }
                            if (result.delta.isNotEmpty() && assistantMessageId != null) {
                                result.partDelta?.let { partDelta ->
                                    if (partDelta.messageId != assistantMessageId) return@let
                                    val kind = assistantPartKinds[partDelta.partId]
                                    if (kind == "reasoning") {
                                        val previous = reasoningPartTexts[partDelta.partId].orEmpty()
                                        reasoningPartTexts[partDelta.partId] = previous + partDelta.delta
                                        fullReasoning.clear()
                                        fullReasoning.append(reasoningPartTexts.values.joinToString(""))
                                        onReasoningUpdated?.invoke(fullReasoning.toString())
                                    } else {
                                        val previous = assistantPartTexts[partDelta.partId].orEmpty()
                                        assistantPartTexts[partDelta.partId] = previous + partDelta.delta
                                    }
                                }
                                val kind = result.partDelta?.let { assistantPartKinds[it.partId] }
                                if (kind != "reasoning") {
                                    fullText.append(result.delta)
                                    Log.i(
                                        TAG,
                                        "stream:delta sessionId=$sessionId len=${result.delta.length} preview=${result.delta.take(60)}",
                                    )
                                    onDelta?.invoke(result.delta)
                                }
                            }
                            val permissionRequest = result.permissionRequest
                            if (permissionRequest != null) {
                                onPermissionRequested?.invoke(permissionRequest)
                            }
                            val todos = result.todos
                            if (todos != null) {
                                onTodoUpdated?.invoke(todos)
                            }
                            if (result.errorMessage != null) {
                                Log.e(TAG, "stream:error sessionId=$sessionId message=${result.errorMessage}")
                                Log.e(TAG, "stream:recent sessionId=$sessionId events=${recentEvents.joinToString(" | ")}")
                                throw RuntimeException(result.errorMessage)
                            }
                            done = result.isCompleted
                            if (done) {
                                doneReason = "event:$eventType"
                                Log.i(TAG, "stream:completed sessionId=$sessionId totalLen=${fullText.length}")
                                Log.i(TAG, "stream:recent sessionId=$sessionId events=${recentEvents.joinToString(" | ")}")
                            }
                            dataBuffer.clear()
                        }
                    }
                }
            }
        }
        Log.i(
            TAG,
            "stream:end sessionId=$sessionId done=$done reason=$doneReason assistantId=${assistantMessageId.orEmpty()} textLen=${fullText.length}",
        )
        return OpenCodeChatMessage(
            id = assistantMessageId ?: UUID.randomUUID().toString(),
            role = "assistant",
            content = fullText.toString(),
            reasoningContent = fullReasoning.toString(),
            reasoningCompleted = true,
            time = System.currentTimeMillis(),
        )
    }

    private data class EventHandleResult(
        val delta: String = "",
        val newAssistantMessageId: String? = null,
        val partDelta: PartDelta? = null,
        val partSnapshot: PartSnapshot? = null,
        val permissionRequest: OpenCodePermissionRequest? = null,
        val todos: List<OpenCodeTodoItem>? = null,
        val isCompleted: Boolean = false,
        val errorMessage: String? = null,
    )

    private data class PartDelta(
        val messageId: String,
        val partId: String,
        val delta: String,
    )

    private data class PartSnapshot(
        val messageId: String,
        val partId: String,
        val kind: String,
        val text: String,
    )

    private fun handleEventData(
        eventData: String,
        sessionId: String,
        currentAssistantMessageId: String?,
        currentText: String,
    ): EventHandleResult {
        val root = runCatching { JSONObject(eventData) }.getOrNull() ?: return EventHandleResult()
        val payload = root.optJSONObject("payload") ?: root
        val type = payload.optString("type")
        val properties = payload.optJSONObject("properties") ?: JSONObject()
        val sessionMatched = matchesSession(properties, sessionId) || matchesSession(payload, sessionId)
        val hasAnySessionHint = hasSessionIdentifier(properties) || hasSessionIdentifier(payload)
        Log.i(TAG, "stream:event type=$type")

        return when (type) {
            "message.updated" -> {
                val info = properties.optJSONObject("info") ?: return EventHandleResult()
                val eventSessionId = properties.optString("sessionID")
                    .ifBlank { info.optString("sessionID") }
                val role = info.optString("role")
                if (eventSessionId == sessionId && role == "assistant") {
                    val assistantId = info.optString("id").takeIf { it.isNotBlank() }
                    Log.i(TAG, "stream:assistant.message id=$assistantId")
                    EventHandleResult(
                        newAssistantMessageId = assistantId,
                    )
                } else {
                    EventHandleResult()
                }
            }
            "message.part.updated" -> {
                val part = properties.optJSONObject("part") ?: return EventHandleResult()
                val eventSessionId = properties.optString("sessionID")
                    .ifBlank { part.optString("sessionID") }
                if (eventSessionId != sessionId) return EventHandleResult()
                val partType = part.optString("type")
                if (partType != "text" && partType != "reasoning") return EventHandleResult()
                val messageId = part.optString("messageID").takeIf { it.isNotBlank() }
                if (messageId == null) return EventHandleResult()
                if (partType == "text" && (currentAssistantMessageId == null || messageId != currentAssistantMessageId)) {
                    return EventHandleResult()
                }
                if (partType == "reasoning" && currentAssistantMessageId != null && messageId != currentAssistantMessageId) {
                    return EventHandleResult()
                }
                val partId = part.optString("id").takeIf { it.isNotBlank() } ?: return EventHandleResult()
                val snapshotText = part.optString("text")
                val fallbackDelta = if (partType == "text") {
                    properties.optString("delta").ifBlank {
                        deriveDeltaFromSnapshot(currentText, snapshotText)
                    }
                } else {
                    ""
                }
                EventHandleResult(
                    delta = fallbackDelta,
                    newAssistantMessageId = if (partType == "reasoning" && currentAssistantMessageId == null) messageId else null,
                    partSnapshot = PartSnapshot(
                        messageId = messageId,
                        partId = partId,
                        kind = partType,
                        text = snapshotText,
                    ),
                )
            }
            "message.part.delta" -> {
                val eventSessionId = properties.optString("sessionID")
                if (eventSessionId != sessionId) return EventHandleResult()
                val field = properties.optString("field")
                if (field.isNotBlank() && field != "text") return EventHandleResult()
                val messageId = properties.optString("messageID").takeIf { it.isNotBlank() }
                    ?: return EventHandleResult()
                if (currentAssistantMessageId == null || messageId != currentAssistantMessageId) return EventHandleResult()
                val partId = properties.optString("partID").takeIf { it.isNotBlank() }
                    ?: return EventHandleResult()
                val delta = properties.optString("delta")
                EventHandleResult(
                    delta = delta,
                    partDelta = PartDelta(
                        messageId = messageId,
                        partId = partId,
                        delta = delta,
                    ),
                )
            }
            "session.error" -> {
                if (!sessionMatched && hasAnySessionHint) return EventHandleResult()
                val message = properties.optJSONObject("error")
                    ?.optJSONObject("data")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "服务端返回错误"
                EventHandleResult(errorMessage = message)
            }
            "session.idle" -> {
                if (sessionMatched || !hasAnySessionHint) {
                    EventHandleResult(isCompleted = true)
                } else {
                    EventHandleResult()
                }
            }
            "session.status" -> {
                if (!sessionMatched && hasAnySessionHint) return EventHandleResult()
                val statusValue = properties.opt("status")
                    ?: return EventHandleResult()
                val statusType = when (statusValue) {
                    is JSONObject -> statusValue.optString("type")
                    is String -> statusValue
                    else -> ""
                }.lowercase()
                val statusMessage = when (statusValue) {
                    is JSONObject -> statusValue.optString("message")
                    else -> ""
                }
                Log.i(TAG, "stream:session.status sessionId=$sessionId type=$statusType message=$statusMessage")
                when (statusType) {
                    "idle" -> EventHandleResult(isCompleted = true)
                    "retry" -> EventHandleResult(errorMessage = statusMessage.ifBlank { "会话执行重试失败" })
                    else -> EventHandleResult()
                }
            }
            "session.completed" -> {
                if (sessionMatched || !hasAnySessionHint) {
                    EventHandleResult(isCompleted = true)
                } else {
                    EventHandleResult()
                }
            }
            "permission.asked" -> {
                val permission = properties.optJSONObject("permission") ?: properties
                val eventSessionId = permission.optString("sessionID")
                if (eventSessionId != sessionId) return EventHandleResult()
                val permissionId = permission.optString("id").ifBlank {
                    permission.optString("permissionID")
                }
                if (permissionId.isBlank()) return EventHandleResult()
                val title = permission.optString("title").ifBlank { "需要权限确认" }
                EventHandleResult(
                    permissionRequest = OpenCodePermissionRequest(
                        sessionId = eventSessionId,
                        permissionId = permissionId,
                        title = title,
                    ),
                )
            }
            "todo.updated" -> {
                val todos = parseTodos(properties, sessionId)
                if (todos == null) EventHandleResult() else EventHandleResult(todos = todos)
            }
            else -> EventHandleResult()
        }
    }

    private fun hasSessionIdentifier(json: JSONObject): Boolean {
        val topLevel = listOf(
            json.optString("id"),
            json.optString("sessionID"),
            json.optString("sessionId"),
            json.optString("session_id"),
        ).any { it.isNotBlank() }
        if (topLevel) return true

        val info = json.optJSONObject("info")
        if (info != null && hasSessionIdentifier(info)) return true

        val session = json.optJSONObject("session")
        if (session != null && hasSessionIdentifier(session)) return true

        val properties = json.optJSONObject("properties")
        if (properties != null && hasSessionIdentifier(properties)) return true

        return false
    }

    private fun matchesSession(properties: JSONObject, sessionId: String): Boolean {
        val topLevel = listOf(
            properties.optString("id"),
            properties.optString("sessionID"),
            properties.optString("sessionId"),
            properties.optString("session_id"),
        ).firstOrNull { it.isNotBlank() }
        if (topLevel == sessionId) return true

        val info = properties.optJSONObject("info")
        val infoSession = listOf(
            info?.optString("sessionID").orEmpty(),
            info?.optString("sessionId").orEmpty(),
            info?.optString("session_id").orEmpty(),
        ).firstOrNull { it.isNotBlank() }
        if (infoSession == sessionId) return true

        val session = properties.optJSONObject("session")
        val nestedSession = listOf(
            session?.optString("id").orEmpty(),
            session?.optString("sessionID").orEmpty(),
            session?.optString("sessionId").orEmpty(),
            session?.optString("session_id").orEmpty(),
        ).firstOrNull { it.isNotBlank() }
        return nestedSession == sessionId
    }

    private fun parseTodos(properties: JSONObject, sessionId: String): List<OpenCodeTodoItem>? {
        val eventSessionId = properties.optString("sessionID")
        if (eventSessionId != sessionId) return null
        val array = properties.optJSONArray("todos") ?: return emptyList()
        return parseTodoArray(array)
    }

    private fun parseTodoArray(array: JSONArray): List<OpenCodeTodoItem> {
        val result = mutableListOf<OpenCodeTodoItem>()
        for (index in 0 until array.length()) {
            when (val item = array.opt(index)) {
                is JSONObject -> {
                    val id = item.optString("id").ifBlank { index.toString() }
                    val text = item.optString("content")
                    if (text.isBlank()) continue
                    val statusText = item.optString("status").lowercase()
                    val done = statusText == "completed"
                    result += OpenCodeTodoItem(id = id, text = text, done = done)
                }
            }
        }
        return result
    }

    private fun deriveDeltaFromSnapshot(current: String, snapshot: String): String {
        if (snapshot.isEmpty()) return ""
        if (current.isEmpty()) return snapshot
        if (snapshot.startsWith(current)) return snapshot.substring(current.length)
        return ""
    }

    private fun getBody(baseUrl: String, path: String, directory: String? = null): String {
        val request = requestWithDirectory(
            Request.Builder(),
            directory,
        )
            .url(baseUrl.endpoint(path))
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            response.requireSuccessful()
            return response.body?.string().orEmpty()
        }
    }

    private fun okhttp3.Response.requireSuccessful() {
        if (!isSuccessful) {
            throw OpenCodeApiException(code, message)
        }
    }

    private fun String.endpoint(path: String): String {
        return trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun requestWithDirectory(builder: Request.Builder, directory: String?): Request.Builder {
        val authed = builder.applyAuth()
        if (directory.isNullOrBlank()) return authed
        val encoded = URLEncoder.encode(directory, StandardCharsets.UTF_8.toString())
        return authed.header("x-opencode-directory", encoded)
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        val header = basicAuthHeader ?: return this
        return header("Authorization", header)
    }

    private companion object {
        const val TAG = "OpenCodeStream"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val STREAM_TIMEOUT_MS = 240_000L
    }
}

class OpenCodeApiException(
    val statusCode: Int,
    statusMessage: String,
) : Exception("OpenCode API request failed with HTTP $statusCode: $statusMessage")
