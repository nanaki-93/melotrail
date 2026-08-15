
package app.melotrail.server.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Files

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = [
    "server.project-storage-path=\${java.io.tmpdir}/ai-music-test-projects",
    "server.audio-storage-path=\${java.io.tmpdir}/ai-music-test-audio"
])
class ProjectControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `project CRUD works`() {
        val create = mockMvc.perform(post("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"title":"Test Project","artist":"Artist"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("Test Project"))
            .andReturn()

        val body = create.response.contentAsString
        val id = Regex(""""id":"([^"]+)"""").find(body)!!.groupValues[1]

        mockMvc.perform(get("/api/projects/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.artist").value("Artist"))

        mockMvc.perform(put("/api/projects/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"title":"Updated"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Updated"))

        mockMvc.perform(delete("/api/projects/$id"))
            .andExpect(status().isNoContent)


    }
}
