package ai.music.workstation.server.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class StaticFrontendRetirementTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `root and former SPA routes are not browser fallbacks while APIs remain available`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/project/retired-browser-route"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/worker/health"))
            .andExpect(status().isOk)
    }
}
