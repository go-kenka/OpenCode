package com.github.go_kenka.opencode.opencode

import android.content.Context
import android.content.SharedPreferences
import com.github.go_kenka.opencode.opencode.model.OpenCodeMode
import com.github.go_kenka.opencode.opencode.model.OpenCodeChatMessage
import com.github.go_kenka.opencode.opencode.model.OpenCodeService
import com.github.go_kenka.opencode.opencode.model.OpenCodeProject
import com.github.go_kenka.opencode.opencode.model.ThinkingLevel
import org.json.JSONArray
import org.json.JSONObject

class OpencodePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getSelectedServiceKey(): String? = prefs.getString(KEY_SELECTED_SERVICE, null)

    fun saveSelectedServiceKey(serviceKey: String) {
        prefs.edit().putString(KEY_SELECTED_SERVICE, serviceKey).apply()
    }

    fun getManualServices(): List<OpenCodeService> {
        val raw = prefs.getString(KEY_MANUAL_SERVICES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val host = item.optString("host").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val port = item.optInt("port").takeIf { it > 0 } ?: return@mapNotNull null
                OpenCodeService(
                    serviceName = item.optString("serviceName").takeIf { it.isNotBlank() } ?: "$host:$port",
                    host = host,
                    port = port,
                    username = item.optString("username").takeIf { it.isNotBlank() },
                    password = item.optString("password").takeIf { it.isNotBlank() },
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveManualServices(services: List<OpenCodeService>) {
        val array = JSONArray()
        services
            .distinctBy { "${it.host}:${it.port}" }
            .forEach { service ->
                array.put(
                    JSONObject()
                        .put("serviceName", service.serviceName)
                        .put("host", service.host)
                        .put("port", service.port)
                        .put("username", service.username)
                        .put("password", service.password),
                )
            }
        prefs.edit().putString(KEY_MANUAL_SERVICES, array.toString()).apply()
    }

    fun getSelectedProjectId(serviceKey: String): String? =
        prefs.getString("$KEY_SELECTED_PROJECT_PREFIX$serviceKey", null)

    fun saveSelectedProjectId(serviceKey: String, projectId: String?) {
        prefs.edit().putString("$KEY_SELECTED_PROJECT_PREFIX$serviceKey", projectId).apply()
    }

    fun getSelectedProjectDirectory(serviceKey: String): String? =
        prefs.getString("$KEY_SELECTED_PROJECT_DIRECTORY_PREFIX$serviceKey", null)

    fun saveSelectedProjectDirectory(serviceKey: String, directory: String?) {
        prefs.edit().putString("$KEY_SELECTED_PROJECT_DIRECTORY_PREFIX$serviceKey", directory).apply()
    }

    fun getSelectedSessionId(serviceKey: String): String? =
        prefs.getString("$KEY_SELECTED_SESSION_PREFIX$serviceKey", null)

    fun saveSelectedSessionId(serviceKey: String, sessionId: String?) {
        prefs.edit().putString("$KEY_SELECTED_SESSION_PREFIX$serviceKey", sessionId).apply()
    }

    fun getLocalProjects(serviceKey: String): List<OpenCodeProject> {
        val raw = prefs.getString("$KEY_LOCAL_PROJECTS_PREFIX$serviceKey", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: id
                val directory = item.optString("directory").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                OpenCodeProject(
                    id = id,
                    name = name,
                    directory = directory,
                    isLocalOnly = true,
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveLocalProjects(serviceKey: String, projects: List<OpenCodeProject>) {
        val array = JSONArray()
        projects
            .filter { it.isLocalOnly }
            .distinctBy { it.directory }
            .forEach { project ->
                array.put(
                    JSONObject()
                        .put("id", project.id)
                        .put("name", project.name)
                        .put("directory", project.directory),
                )
            }
        prefs.edit().putString("$KEY_LOCAL_PROJECTS_PREFIX$serviceKey", array.toString()).apply()
    }

    fun getSessionSelection(serviceKey: String, projectId: String?, sessionId: String?): SessionSelection {
        val scope = selectionScope(serviceKey, projectId, sessionId)
        val modeValue = prefs.getString("$KEY_MODE_PREFIX$scope", null)
        val thinkingValue = prefs.getString("$KEY_THINKING_PREFIX$scope", null)
        val modelProviderID = prefs.getString("$KEY_MODEL_PROVIDER_PREFIX$scope", null)
        val modelID = prefs.getString("$KEY_MODEL_ID_PREFIX$scope", null)

        return SessionSelection(
            modeAgent = modeValue,
            thinkingLevel = thinkingValue?.let { runCatching { ThinkingLevel.valueOf(it) }.getOrNull() },
            modelProviderID = modelProviderID,
            modelID = modelID,
        )
    }

    fun saveSessionSelection(
        serviceKey: String,
        projectId: String?,
        sessionId: String?,
        mode: OpenCodeMode,
        thinkingLevel: ThinkingLevel,
        modelProviderID: String?,
        modelID: String?,
    ) {
        val scope = selectionScope(serviceKey, projectId, sessionId)
        prefs.edit()
            .putString("$KEY_MODE_PREFIX$scope", mode.agent)
            .putString("$KEY_THINKING_PREFIX$scope", thinkingLevel.name)
            .putString("$KEY_MODEL_PROVIDER_PREFIX$scope", modelProviderID)
            .putString("$KEY_MODEL_ID_PREFIX$scope", modelID)
            .apply()
    }

    fun getSessionMessages(serviceKey: String, sessionId: String): List<OpenCodeChatMessage> {
        if (sessionId.isBlank()) return emptyList()
        val raw = prefs.getString("$KEY_SESSION_MESSAGES_PREFIX$serviceKey|$sessionId", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val role = item.optString("role").takeIf { it == "assistant" || it == "user" } ?: return@mapNotNull null
                val content = item.optString("content")
                if (content.isBlank()) return@mapNotNull null
                OpenCodeChatMessage(
                    id = id,
                    role = role,
                    content = content,
                    isError = item.optBoolean("isError", false),
                    time = item.optLong("time", System.currentTimeMillis()),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveSessionMessages(
        serviceKey: String,
        sessionId: String,
        messages: List<OpenCodeChatMessage>,
    ) {
        if (sessionId.isBlank()) return
        val array = JSONArray()
        messages.takeLast(MAX_SAVED_MESSAGES_PER_SESSION).forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("role", message.role)
                    .put("content", message.content)
                    .put("isError", message.isError)
                    .put("time", message.time),
            )
        }
        prefs.edit().putString("$KEY_SESSION_MESSAGES_PREFIX$serviceKey|$sessionId", array.toString()).apply()
    }

    private fun selectionScope(serviceKey: String, projectId: String?, sessionId: String?): String {
        val safeProject = projectId ?: "_"
        val safeSession = sessionId ?: "_"
        return "$serviceKey|$safeProject|$safeSession"
    }

    data class SessionSelection(
        val modeAgent: String?,
        val thinkingLevel: ThinkingLevel?,
        val modelProviderID: String?,
        val modelID: String?,
    )

    companion object {
        private const val PREF_NAME = "opencode_prefs"
        private const val KEY_SELECTED_SERVICE = "selected_service"
        private const val KEY_MANUAL_SERVICES = "manual_services"
        private const val KEY_SELECTED_PROJECT_PREFIX = "selected_project:"
        private const val KEY_SELECTED_PROJECT_DIRECTORY_PREFIX = "selected_project_directory:"
        private const val KEY_SELECTED_SESSION_PREFIX = "selected_session:"
        private const val KEY_LOCAL_PROJECTS_PREFIX = "local_projects:"
        private const val KEY_MODE_PREFIX = "selection_mode:"
        private const val KEY_THINKING_PREFIX = "selection_thinking:"
        private const val KEY_MODEL_PROVIDER_PREFIX = "selection_model_provider:"
        private const val KEY_MODEL_ID_PREFIX = "selection_model_id:"
        private const val KEY_SESSION_MESSAGES_PREFIX = "session_messages:"
        private const val MAX_SAVED_MESSAGES_PER_SESSION = 200
    }
}
