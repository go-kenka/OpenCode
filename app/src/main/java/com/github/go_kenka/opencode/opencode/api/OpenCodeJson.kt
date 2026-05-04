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

import com.github.go_kenka.opencode.opencode.model.OpenCodeChatMessage
import com.github.go_kenka.opencode.opencode.model.OpenCodeDirectoryEntry
import com.github.go_kenka.opencode.opencode.model.OpenCodeMode
import com.github.go_kenka.opencode.opencode.model.OpenCodeModelOption
import com.github.go_kenka.opencode.opencode.model.OpenCodeProject
import com.github.go_kenka.opencode.opencode.model.OpenCodeSession
import com.github.go_kenka.opencode.opencode.model.OpenCodeVcsInfo
import com.github.go_kenka.opencode.opencode.model.ThinkingLevel
import org.json.JSONArray
import org.json.JSONObject

object OpenCodeJson {
    fun parsePathDirectories(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        return listOf("directory", "worktree", "home")
            .mapNotNull { key -> root.optString(key).takeIf { it.isNotBlank() } }
            .distinct()
    }

    fun parseDirectoryEntries(json: String, baseDirectory: String): List<OpenCodeDirectoryEntry> {
        if (json.isBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val result = linkedMapOf<String, OpenCodeDirectoryEntry>()
        for (index in 0 until array.length()) {
            val item = array.optString(index).takeIf { it.isNotBlank() } ?: continue
            val directory = normalizeDirectory(baseDirectory, item)
            val name = directory.substringAfterLast('/').ifBlank { directory }
            result[directory] = OpenCodeDirectoryEntry(name = name, directory = directory)
        }
        return result.values.toList()
    }

    fun parseAgents(json: String): List<OpenCodeMode> {
        if (json.isBlank()) return OpenCodeMode.defaults

        val result = linkedMapOf<String, OpenCodeMode>()

        fun addMode(agent: String?, label: String? = null) {
            val id = agent?.trim().orEmpty()
            if (id.isBlank()) return
            val normalized = id.lowercase()
            if (normalized in listOf("compaction", "summary", "title")) return
            val display = label?.trim().takeUnless { it.isNullOrBlank() } ?: id
            result.putIfAbsent(normalized, OpenCodeMode(agent = id, label = display))
        }

        fun parseAgentObject(obj: JSONObject) {
            if (obj.optBoolean("hidden", false)) return
            val name = obj.optString("name").takeIf { it.isNotBlank() }
                ?: obj.optString("id").takeIf { it.isNotBlank() }
                ?: obj.optString("agent").takeIf { it.isNotBlank() }
                ?: obj.optString("key").takeIf { it.isNotBlank() }
            val label = obj.optString("name").takeIf { it.isNotBlank() } ?: name
            addMode(name, label)
        }

        val array = runCatching { JSONArray(json) }.getOrNull() ?: return OpenCodeMode.defaults
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseAgentObject(item)
        }

        if (!result.containsKey("build")) {
            result["build"] = OpenCodeMode.defaultBuild
        }
        return result.values.toList()
    }

    fun parseProjects(json: String): List<OpenCodeProject> {
        if (json.isBlank()) return emptyList()
        return parseProjectArray(JSONArray(json))
    }

    fun parseProject(json: String): OpenCodeProject? {
        if (json.isBlank()) return null
        return parseProjectObject(JSONObject(json))
    }

    fun parseModels(json: String): List<OpenCodeModelOption> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val providers = root.optJSONArray("providers") ?: return emptyList()
        return parseProviderArrayModels(providers)
    }

    fun parseSessions(json: String): List<OpenCodeSession> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            parseSessionOrNull(array.getJSONObject(index))
        }
    }

    fun parseSession(json: String): OpenCodeSession {
        return parseSessionOrNull(JSONObject(json)) ?: OpenCodeSession(
            id = JSONObject(json).optString("id"),
            projectID = null,
            directory = null,
            title = null,
            time = 0L,
        )
    }

    fun parseMessages(json: String): List<OpenCodeChatMessage> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::parseMessageObject)
        }
    }

    fun parseMessageResponse(json: String): OpenCodeChatMessage = parseMessage(json)

    fun parseVcsInfo(json: String): OpenCodeVcsInfo {
        if (json.isBlank()) return OpenCodeVcsInfo()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return OpenCodeVcsInfo()
        val vcs = root.optJSONObject("vcs") ?: root.optJSONObject("data") ?: root
        val branch = vcs.firstNonBlank("branch", "ref", "name")
        val directSummary = vcs.firstNonBlank("statusSummary", "summary")
        val status = vcs.firstNonBlank("status")
        val clean = vcs.opt("clean")
        val statusSummary = when {
            !directSummary.isNullOrBlank() -> directSummary
            !status.isNullOrBlank() -> status
            clean is Boolean -> if (clean) "工作区干净" else "有未提交改动"
            else -> null
        }
        return OpenCodeVcsInfo(
            branch = branch,
            statusSummary = statusSummary,
        )
    }

    fun parseFileStatusSummary(json: String): String? {
        if (json.isBlank()) return null
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
        if (array.length() == 0) return "工作区干净"
        var added = 0
        var modified = 0
        var deleted = 0
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            when (item.optString("status").lowercase()) {
                "added" -> added++
                "deleted" -> deleted++
                "modified" -> modified++
            }
        }
        val parts = mutableListOf<String>()
        if (added > 0) parts += "新增$added"
        if (modified > 0) parts += "修改$modified"
        if (deleted > 0) parts += "删除$deleted"
        return if (parts.isNotEmpty()) parts.joinToString("，") else "有未提交改动"
    }

    fun parseMessage(json: String): OpenCodeChatMessage {
        return parseMessageObject(JSONObject(json)) ?: OpenCodeChatMessage(
            id = "assistant",
            role = "assistant",
            content = "",
        )
    }

    fun buildSendMessageJson(
        content: String,
        mode: OpenCodeMode,
        model: OpenCodeModelOption?,
        thinkingLevel: ThinkingLevel,
    ): String = buildMessageRequest(content, mode, model, thinkingLevel)

    @Suppress("UNUSED_PARAMETER")
    fun buildMessageRequest(
        prompt: String,
        mode: OpenCodeMode,
        model: OpenCodeModelOption?,
        thinkingLevel: ThinkingLevel,
    ): String {
        val root = JSONObject()
            .put("agent", mode.agent)
            .put(
                "parts",
                JSONArray().put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", prompt),
                ),
            )

        if (model?.providerID != null && model.modelID != null) {
            root.put(
                "model",
                JSONObject()
                    .put("providerID", model.providerID)
                    .put("modelID", model.modelID),
            )
        }

        return root.toString()
    }

    fun buildCreateSessionJson(title: String? = null): String {
        val root = JSONObject()
        if (!title.isNullOrBlank()) root.put("title", title)
        return root.toString()
    }

    fun buildCommandRequestJson(
        command: String,
        arguments: List<String> = emptyList(),
        messageId: String? = null,
        agent: String? = null,
        model: OpenCodeModelOption? = null,
    ): String {
        val root = JSONObject()
            .put("command", command)
            .put("arguments", arguments.joinToString(" "))
        if (!messageId.isNullOrBlank()) root.put("messageID", messageId)
        if (!agent.isNullOrBlank()) root.put("agent", agent)
        if (model?.providerID != null && model.modelID != null) {
            root.put("model", "${model.providerID}/${model.modelID}")
        }
        return root.toString()
    }

    private fun parseProjectArray(array: JSONArray): List<OpenCodeProject> {
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::parseProjectObject)
        }
    }

    private fun parseProjectObject(json: JSONObject): OpenCodeProject? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val directory = json.optString("worktree").takeIf { it.isNotBlank() }
            ?: json.optString("directory").takeIf { it.isNotBlank() }
            ?: json.optString("path").takeIf { it.isNotBlank() }
            ?: return null
        val gitObject = json.optJSONObject("git")
        return OpenCodeProject(
            id = id,
            name = json.optString("name").takeIf { it.isNotBlank() } ?: directory.substringAfterLast('/'),
            directory = directory,
            gitBranch = json.firstNonBlank("gitBranch", "branch")
                ?: gitObject?.firstNonBlank("branch", "name", "ref"),
            gitStatusSummary = json.firstNonBlank("gitStatus", "gitStatusSummary")
                ?: gitObject?.firstNonBlank("status", "summary"),
        )
    }

    private fun parseMessageObject(root: JSONObject): OpenCodeChatMessage? {
        val info = root.optJSONObject("info") ?: JSONObject()
        val parts = root.optJSONArray("parts") ?: JSONArray()
        val role = info.optString("role", "assistant")
        if (role != "assistant" && role != "user") return null
        val content = buildString {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                if (part.optString("type") == "text") {
                    append(part.optString("text"))
                }
            }
        }.trim()
        val reasoning = buildString {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                if (part.optString("type") == "reasoning") {
                    append(part.optString("text"))
                }
            }
        }.trim()
        if (content.isBlank()) return null
        val time = parseMessageTime(root, info)
        return OpenCodeChatMessage(
            id = info.optString("id", role),
            role = role,
            content = content,
            reasoningContent = reasoning,
            reasoningCompleted = true,
            time = time,
        )
    }

    private fun parseMessageTime(root: JSONObject, info: JSONObject): Long {
        val fallback = System.currentTimeMillis()
        fun normalize(value: Long): Long {
            if (value <= 0L) return fallback
            return if (value < 10_000_000_000L) value * 1000 else value
        }

        val raw = sequenceOf(
            info.optLong("time", 0L),
            info.optLong("created", 0L),
            info.optLong("createdAt", 0L),
            info.optLong("updated", 0L),
            root.optLong("time", 0L),
            root.optLong("created", 0L),
            root.optLong("createdAt", 0L),
            root.optLong("updated", 0L),
            root.optJSONObject("time")?.optLong("created", 0L) ?: 0L,
            root.optJSONObject("time")?.optLong("updated", 0L) ?: 0L,
        ).firstOrNull { it > 0L } ?: 0L
        return normalize(raw)
    }

    private fun parseProviderArrayModels(array: JSONArray): List<OpenCodeModelOption> {
        val models = mutableListOf<OpenCodeModelOption>()
        for (index in 0 until array.length()) {
            val provider = array.optJSONObject(index) ?: continue
            if (!provider.isEnabledProvider()) continue
            val providerID = provider.optString("id").takeIf { it.isNotBlank() }
                ?: provider.optString("providerID").takeIf { it.isNotBlank() }
                ?: continue
            val providerName = provider.optString("name").takeIf { it.isNotBlank() } ?: providerID
            models += parseProviderModels(providerID, providerName, provider)
        }
        return models.distinctBy { "${it.providerID}:${it.modelID}" }
    }

    private fun parseProviderModels(
        providerID: String,
        providerName: String,
        provider: JSONObject,
    ): List<OpenCodeModelOption> {
        val models = mutableListOf<OpenCodeModelOption>()
        when (val providerModels = provider.opt("models")) {
            is JSONObject -> providerModels.keys().forEach { modelID ->
                val model = providerModels.optJSONObject(modelID)
                if (model != null && !model.isActiveModel()) return@forEach
                val label = model?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: model?.optString("displayName")?.takeIf { it.isNotBlank() }
                    ?: modelID
                val modelProvider = model?.optString("providerID")?.takeIf { it.isNotBlank() } ?: providerID
                val actualModelID = model?.optString("id")?.takeIf { it.isNotBlank() } ?: modelID
                models += OpenCodeModelOption(
                    providerID = modelProvider,
                    modelID = actualModelID,
                    label = label,
                    providerName = providerName,
                    isFree = isOpenCodeZenFreeModel(providerName, actualModelID, label),
                )
            }
            is JSONArray -> for (index in 0 until providerModels.length()) {
                val model = providerModels.optJSONObject(index) ?: continue
                if (!model.isActiveModel()) continue
                val modelID = model.optString("id").takeIf { it.isNotBlank() }
                    ?: model.optString("modelID").takeIf { it.isNotBlank() }
                    ?: continue
                val modelProvider = model.optString("providerID").takeIf { it.isNotBlank() } ?: providerID
                val label = model.optString("name").takeIf { it.isNotBlank() } ?: modelID
                models += OpenCodeModelOption(
                    providerID = modelProvider,
                    modelID = modelID,
                    label = label,
                    providerName = providerName,
                    isFree = isOpenCodeZenFreeModel(providerName, modelID, label),
                )
            }
            else -> Unit
        }
        return models
    }

    private fun JSONObject.isEnabledProvider(): Boolean {
        if (has("enabled")) return optBoolean("enabled", false)
        if (has("disabled")) return !optBoolean("disabled", false)
        if (has("status")) {
            val status = optString("status")
            return status.isBlank() || status == "active" || status == "enabled"
        }
        return true
    }

    private fun JSONObject.isActiveModel(): Boolean {
        val status = optString("status")
        return status.isBlank() || status == "active"
    }

    private fun isOpenCodeZenFreeModel(providerName: String, modelID: String, label: String): Boolean {
        if (!providerName.contains("OpenCode Zen", ignoreCase = true)) return false
        return modelID.contains("free", ignoreCase = true) || label.contains("free", ignoreCase = true)
    }

    private fun parseSessionOrNull(json: JSONObject): OpenCodeSession? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val timeValue = json.opt("time")
        val updatedTime = when (timeValue) {
            is JSONObject -> timeValue.optLong("updated", timeValue.optLong("created", 0L))
            is Number -> timeValue.toLong()
            else -> 0L
        }
        val projectObject = json.optJSONObject("project")
        val gitObject = json.optJSONObject("git")
            ?: projectObject?.optJSONObject("git")
        val projectId = json.firstNonBlank(
            "projectID",
            "projectId",
            "project_id",
        ) ?: projectObject?.firstNonBlank("id", "projectID", "projectId", "project_id")
        val directory = json.firstNonBlank(
            "directory",
            "worktree",
            "path",
        ) ?: projectObject?.firstNonBlank("worktree", "directory", "path")
        val title = json.firstNonBlank("title", "name")
            ?: projectObject?.firstNonBlank("name", "title")
        val gitBranch = json.firstNonBlank("gitBranch", "branch")
            ?: projectObject?.firstNonBlank("gitBranch", "branch")
            ?: gitObject?.firstNonBlank("branch", "name", "ref")
        val gitStatusSummary = json.firstNonBlank("gitStatus", "gitStatusSummary", "status")
            ?: projectObject?.firstNonBlank("gitStatus", "gitStatusSummary")
            ?: gitObject?.firstNonBlank("status", "summary")
        return OpenCodeSession(
            id = id,
            projectID = projectId,
            directory = directory,
            title = title,
            time = updatedTime,
            gitBranch = gitBranch,
            gitStatusSummary = gitStatusSummary,
        )
    }

    private fun JSONObject.firstNonBlank(vararg keys: String): String? {
        for (key in keys) {
            val value = optString(key).takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }

    private fun normalizeDirectory(baseDirectory: String, rawPath: String): String {
        val cleanBase = baseDirectory.trim().trimEnd('/').ifBlank { "/" }
        val cleanPath = rawPath.trim().trimEnd('/')
        if (cleanPath.startsWith("/")) return cleanPath.ifBlank { "/" }
        return "$cleanBase/${cleanPath.trimStart('/')}".replace("//", "/")
    }
}
