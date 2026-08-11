package ai.music.workstation.model

import ai.music.workstation.model.Project
import ai.music.workstation.model.ProjectTrack
import kotlinx.datetime.Instant
import java.nio.file.Files
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class ProjectServiceTest {
    private val projectDir = java.nio.file.Paths.get("/tmp/test-projects")

    @Test
    fun `should create project`() {
        val service = ProjectServiceImpl(projectDir)
        val project = service.create("Test Project", "Test Artist")

        assertNotNull(project)
        assertEquals("Test Project", project.title)
        assertEquals("Test Artist", project.artist)
        assertTrue(project.id.isNotEmpty())
    }

    @Test
    fun `should save and load project`() {
        val service = ProjectServiceImpl(projectDir)
        val project = service.create("Test Project", "Test Artist")

        val loaded = service.load(project.id)
        assertTrue(loaded.isSuccess)
        assertEquals(project.title, loaded.getOrNull()?.title)
    }

    @Test
    fun `should list projects`() {
        val service = ProjectServiceImpl(projectDir)
        service.create("Project 1", "Artist 1")
        service.create("Project 2", "Artist 2")

        val projects = service.list()
        assertTrue(projects.size >= 2)
    }

    @Test
    fun `should delete project`() {
        val service = ProjectServiceImpl(projectDir)
        val project = service.create("Test Project", "Test Artist")

        val result = service.delete(project.id)
        assertTrue(result.isSuccess)

        val loadResult = service.load(project.id)
        assertTrue(loadResult.isFailure)
    }

    @Test
    fun `should add track to project`() {
        val service = ProjectServiceImpl(projectDir)
        val project = service.create("Test Project", "Test Artist")

        val track = ProjectTrack(
            id = "track-1",
            name = "Drums",
            type = TrackType.DRUMS
        )
        val updated = project.addTrack(track)
        assertEquals(1, updated.tracks.size)
        assertEquals("Drums", updated.tracks[0].name)
    }
}
