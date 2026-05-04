package com.github.go_kenka.opencode.opencode.api

import com.github.go_kenka.opencode.opencode.model.OpenCodeMode
import com.github.go_kenka.opencode.opencode.model.OpenCodeModelOption
import com.github.go_kenka.opencode.opencode.model.OpenCodeSession
import com.github.go_kenka.opencode.opencode.model.ThinkingLevel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeJsonTest {
    @Test
    fun parsesMessageResponseByJoiningOnlyTextParts() {
        val json = """
            {
              "info": { "role": "assistant", "id": "message-1" },
              "parts": [
                { "type": "text", "text": "Hello" },
                { "type": "tool", "text": "ignored" },
                { "type": "text", "text": " world" }
              ]
            }
        """.trimIndent()

        val message = OpenCodeJson.parseMessageResponse(json)

        assertEquals("message-1", message.id)
        assertEquals("assistant", message.role)
        assertEquals("Hello world", message.content)
        assertFalse(message.isError)
    }

    @Test
    fun buildsSendMessageJsonWithAgentPartsAndModelWhenSelected() {
        val json = JSONObject(
            OpenCodeJson.buildSendMessageJson(
                content = "Ship it",
                mode = OpenCodeMode.defaultBuild,
                model = OpenCodeModelOption(
                    providerID = "google",
                    modelID = "gemini-3-pro-preview",
                    label = "Gemini 3 Pro Preview",
                ),
                thinkingLevel = ThinkingLevel.HIGH,
            ),
        )

        assertEquals("build", json.getString("agent"))
        assertEquals("text", json.getJSONArray("parts").getJSONObject(0).getString("type"))
        assertEquals("Ship it", json.getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals("google", json.getJSONObject("model").getString("providerID"))
        assertEquals("gemini-3-pro-preview", json.getJSONObject("model").getString("modelID"))
        assertFalse(json.has("thinkingLevel"))
    }

    @Test
    fun omitsModelWhenModelIsNull() {
        val json = JSONObject(
            OpenCodeJson.buildSendMessageJson(
                content = "Plan it",
                mode = OpenCodeMode(agent = "plan", label = "plan"),
                model = null,
                thinkingLevel = ThinkingLevel.LOW,
            ),
        )

        assertEquals("plan", json.getString("agent"))
        assertFalse(json.has("model"))
        assertFalse(json.has("thinkingLevel"))
    }

    @Test
    fun parsesSessionJsonUsingOpenCodeFieldNames() {
        val session = OpenCodeJson.parseSession(
            """
                {
                  "id": "session-1",
                  "projectID": "project-1",
                  "directory": "/repo/app",
                  "title": "App",
                  "time": 12345
                }
            """.trimIndent(),
        )

        assertEquals(OpenCodeSession("session-1", "project-1", "/repo/app", "App", 12345L), session)
        assertNull(OpenCodeJson.parseSession("{ \"id\": \"missing-directory\" }").directory)
    }

    @Test
    fun parsesSessionJsonUsingCamelAndNestedProjectFields() {
        val session = OpenCodeJson.parseSession(
            """
                {
                  "id": "session-2",
                  "projectId": "project-2",
                  "project": {
                    "id": "project-2",
                    "worktree": "/repo/web",
                    "name": "Web"
                  },
                  "time": { "created": 10, "updated": 20 }
                }
            """.trimIndent(),
        )

        assertEquals(OpenCodeSession("session-2", "project-2", "/repo/web", "Web", 20L), session)
    }

    @Test
    fun parsesProjectListFromSdkShape() {
        val projects = OpenCodeJson.parseProjects(
            """
                [
                  {
                    "id": "proj-1",
                    "worktree": "/Users/wu/work/front-xd-keban",
                    "vcs": "git",
                    "time": { "created": 10, "updated": 20 }
                  }
                ]
            """.trimIndent(),
        )

        assertEquals(1, projects.size)
        assertEquals("proj-1", projects[0].id)
        assertEquals("front-xd-keban", projects[0].name)
        assertEquals("/Users/wu/work/front-xd-keban", projects[0].directory)
        assertNull(projects[0].sessionId)
    }

    @Test
    fun parsesProviderModelsFromMapShape() {
        val models = OpenCodeJson.parseModels(
            """
                {
                  "google": {
                    "name": "Google",
                    "models": {
                      "gemini-3-pro-preview": { "name": "Gemini 3 Pro Preview" },
                      "gemini-2.5-pro": { "name": "Gemini 2.5 Pro" }
                    }
                  }
                }
            """.trimIndent(),
        )

        assertTrue(models.any { it.providerID == "google" && it.modelID == "gemini-3-pro-preview" && it.label == "Gemini 3 Pro Preview" })
        assertTrue(models.any { it.providerID == "google" && it.modelID == "gemini-2.5-pro" && it.label == "Gemini 2.5 Pro" })
    }

    @Test
    fun parsesOnlyEnabledProvidersAndActiveModelsFromProviderResponse() {
        val models = OpenCodeJson.parseModels(
            """
                {
                  "all": [
                    {
                      "id": "enabled-provider",
                      "enabled": true,
                      "models": {
                        "active-model": {
                          "id": "active-model",
                          "providerID": "enabled-provider",
                          "name": "Active Model",
                          "status": "active"
                        },
                        "disabled-model": {
                          "id": "disabled-model",
                          "providerID": "enabled-provider",
                          "name": "Disabled Model",
                          "status": "disabled"
                        }
                      }
                    },
                    {
                      "id": "disabled-provider",
                      "enabled": false,
                      "models": {
                        "active-but-provider-disabled": {
                          "id": "active-but-provider-disabled",
                          "providerID": "disabled-provider",
                          "name": "Hidden Model",
                          "status": "active"
                        }
                      }
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertTrue(models.any { it.providerID == "enabled-provider" && it.modelID == "active-model" })
        assertFalse(models.any { it.modelID == "disabled-model" })
        assertFalse(models.any { it.providerID == "disabled-provider" })
    }

    @Test
    fun marksOpenCodeZenFreeModels() {
        val models = OpenCodeJson.parseModels(
            """
                {
                  "providers": [
                    {
                      "id": "opencode-zen",
                      "name": "OpenCode Zen",
                      "enabled": true,
                      "models": {
                        "zen-smart-free": {
                          "id": "zen-smart-free",
                          "providerID": "opencode-zen",
                          "name": "Zen Smart Free",
                          "status": "active"
                        },
                        "zen-pro": {
                          "id": "zen-pro",
                          "providerID": "opencode-zen",
                          "name": "Zen Pro",
                          "status": "active"
                        }
                      }
                    }
                  ]
                }
            """.trimIndent(),
        )

        val freeModel = models.first { it.modelID == "zen-smart-free" }
        val paidModel = models.first { it.modelID == "zen-pro" }
        assertTrue(freeModel.isFree)
        assertEquals("OpenCode Zen", freeModel.providerName)
        assertEquals("Zen Smart Free (Free)", freeModel.displayLabel)
        assertFalse(paidModel.isFree)
    }
}
