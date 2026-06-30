package com.github.gaomond.topo.adapter.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ConfigController::class)
class ConfigControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun test_getConfig_always_returns200AndApplicationJson() {
        mockMvc
            .get("/api/config")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun test_getConfig_objectTypes_returnsArrayWithShrineOnly() {
        mockMvc
            .get("/api/config")
            .andExpect {
                jsonPath("$.objectTypes.length()") { value(1) }
                jsonPath("$.objectTypes[0]") { value("shrine") }
            }
    }

    @Test
    fun test_getConfig_areaPresets_returnsThreeInSmallMediumLargeOrderWithValues() {
        mockMvc
            .get("/api/config")
            .andExpect {
                jsonPath("$.areaPresets.length()") { value(3) }

                jsonPath("$.areaPresets[0].key") { value("small") }
                jsonPath("$.areaPresets[0].label") { value("お手軽") }
                jsonPath("$.areaPresets[0].sqm") { value(500000) }

                jsonPath("$.areaPresets[1].key") { value("medium") }
                jsonPath("$.areaPresets[1].label") { value("ふつう") }
                jsonPath("$.areaPresets[1].sqm") { value(2000000) }

                jsonPath("$.areaPresets[2].key") { value("large") }
                jsonPath("$.areaPresets[2].label") { value("がっつり") }
                jsonPath("$.areaPresets[2].sqm") { value(10000000) }
            }
    }
}
