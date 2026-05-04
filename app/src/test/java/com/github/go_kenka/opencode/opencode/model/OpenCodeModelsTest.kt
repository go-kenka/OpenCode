package com.github.go_kenka.opencode.opencode.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeModelsTest {
    @Test
    fun serviceBuildsHttpBaseUrlFromHostAndPort() {
        val service = OpenCodeService(
            serviceName = "desk",
            host = "192.168.1.2",
            port = 4096,
        )

        assertEquals("http://192.168.1.2:4096", service.baseUrl)
    }

    @Test
    fun aggregatesProjectsFromSessionsUsingLatestSessionPerProject() {
        val sessions = listOf(
            OpenCodeSession(
                id = "session-old",
                projectID = "project-1",
                directory = "/work/alpha",
                title = "Alpha Old",
                time = 100L,
            ),
            OpenCodeSession(
                id = "session-new",
                projectID = "project-1",
                directory = "/work/alpha",
                title = "Alpha",
                time = 200L,
            ),
            OpenCodeSession(
                id = "session-beta",
                projectID = null,
                directory = "/work/beta",
                title = null,
                time = 150L,
            ),
        )

        val projects = OpenCodeProject.fromSessions(sessions)

        assertEquals(2, projects.size)
        assertEquals(
            OpenCodeProject(
                id = "project-1",
                name = "alpha",
                directory = "/work/alpha",
                sessionId = "session-new",
                isLocalOnly = true,
            ),
            projects[0],
        )
        assertEquals(
            OpenCodeProject(
                id = "local::/work/beta",
                name = "beta",
                directory = "/work/beta",
                sessionId = "session-beta",
                isLocalOnly = true,
            ),
            projects[1],
        )
    }

    @Test
    fun mergesMissingProjectsFromSessionsWhenServerProjectListIsIncomplete() {
        val serverProjects = listOf(
            OpenCodeProject(
                id = "global",
                name = "global",
                directory = "/",
                sessionId = null,
            ),
        )
        val sessions = listOf(
            OpenCodeSession(
                id = "session-hetu",
                projectID = "project-hetu",
                directory = "/Users/wu/Documents/GitHub/hetu",
                title = "hetu",
                time = 200L,
            ),
        )

        val merged = OpenCodeProject.mergeWithSessions(serverProjects, sessions)

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.id == "global" })
        assertTrue(
            merged.any {
                it.id == "project-hetu" &&
                    it.directory == "/Users/wu/Documents/GitHub/hetu" &&
                    it.sessionId == "session-hetu"
            },
        )
    }

    @Test
    fun groupsSessionsByDirectoryWhenSameProjectHasMultipleSessions() {
        val sessions = listOf(
            OpenCodeSession(
                id = "session-1",
                projectID = "project-a",
                directory = "/work/alpha",
                title = "Alpha One",
                time = 100L,
            ),
            OpenCodeSession(
                id = "session-2",
                projectID = "project-b",
                directory = "/work/alpha",
                title = "Alpha Two",
                time = 200L,
            ),
        )

        val projects = OpenCodeProject.fromSessions(sessions)

        assertEquals(1, projects.size)
        assertEquals("/work/alpha", projects[0].directory)
        assertEquals("session-2", projects[0].sessionId)
        assertEquals("project-b", projects[0].id)
    }

    @Test
    fun modeExposesApiAgentAndUiLabel() {
        val build = OpenCodeMode.defaultBuild
        val plan = OpenCodeMode(agent = "plan", label = "plan")
        assertEquals("build", build.agent)
        assertEquals("build", build.label)
        assertEquals("plan", plan.agent)
        assertEquals("plan", plan.label)
    }

    @Test
    fun defaultModelsIncludeGeminiPreview() {
        val models = OpenCodeModelOption.defaults

        assertTrue(models.any { it.providerID == "google" && it.modelID == "gemini-3-pro-preview" && it.label == "Gemini 3 Pro Preview" })
        assertFalse(models.any { it.label == "Server Default" })
    }

    @Test
    fun thinkingLevelsExposeChineseLabels() {
        assertEquals("默认", ThinkingLevel.DEFAULT.label)
        assertEquals("低", ThinkingLevel.LOW.label)
        assertEquals("中", ThinkingLevel.MEDIUM.label)
        assertEquals("高", ThinkingLevel.HIGH.label)
    }

    @Test
    fun chatMessageCarriesIdentityRoleContentAndErrorFlag() {
        val message = OpenCodeChatMessage(
            id = "msg-1",
            role = "assistant",
            content = "failed",
            isError = true,
        )

        assertEquals("msg-1", message.id)
        assertEquals("assistant", message.role)
        assertEquals("failed", message.content)
        assertTrue(message.isError)
        assertFalse(message.copy(isError = false).isError)
    }
}
