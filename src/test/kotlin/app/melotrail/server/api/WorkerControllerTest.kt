
package app.melotrail.server.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.junit.jupiter.api.Assertions.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class WorkerControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `worker command endpoint preserves API`() {
        mockMvc.perform(post("/api/worker/command")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"command":"pause","jobId":"job-123"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.jobId").value("job-123"))
    }

    @Test
    fun `worker health endpoint is available`() {
        val result = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/worker/health")
        ).andExpect(status().isOk).andReturn()
        assertTrue(result.response.contentAsString.contains("workerRunning"))
    }
}
