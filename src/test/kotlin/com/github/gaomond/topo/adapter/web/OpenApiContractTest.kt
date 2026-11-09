package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.usecase.ConfirmLocationUseCase
import com.github.gaomond.topo.usecase.CreateGameUseCase
import com.github.gaomond.topo.usecase.GetGameStateResult
import com.github.gaomond.topo.usecase.GetGameStateUseCase
import com.github.gaomond.topo.usecase.JoinGameInput
import com.github.gaomond.topo.usecase.JoinGameResult
import com.github.gaomond.topo.usecase.JoinGameUseCase
import com.github.gaomond.topo.usecase.StartGameUseCase
import com.github.gaomond.topo.usecase.UpdateLiveLocationUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.configuration.SpringDocJacksonKotlinModuleConfiguration
import org.springdoc.core.configuration.SpringDocKotlinConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springdoc.core.properties.SwaggerUiOAuthProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springdoc.webmvc.ui.SwaggerConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@WebMvcTest(
    controllers = [
        ConfigController::class,
        CreateGameController::class,
        JoinGameController::class,
        StartGameController::class,
        GameStateController::class,
        UpdateLocationController::class,
        ConfirmLocationController::class,
    ],
)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
    SpringDocKotlinConfiguration::class,
    SpringDocJacksonKotlinModuleConfiguration::class,
    SwaggerConfig::class,
    SwaggerUiConfigProperties::class,
    SwaggerUiOAuthProperties::class,
)
class OpenApiContractTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var createGameUseCase: CreateGameUseCase

    @MockitoBean
    lateinit var joinGameUseCase: JoinGameUseCase

    @MockitoBean
    lateinit var startGameUseCase: StartGameUseCase

    @MockitoBean
    lateinit var getGameStateUseCase: GetGameStateUseCase

    @MockitoBean
    lateinit var updateLiveLocationUseCase: UpdateLiveLocationUseCase

    @MockitoBean
    lateinit var confirmLocationUseCase: ConfirmLocationUseCase

    @Test
    fun test_getApiDocs_existingControllers_returnsGeneratedSpecification() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/config'].get.operationId") { value("getConfig") }
        }
    }

    @Test
    fun test_getApiDocs_existingApi_exposesOnlySevenOperationsAndTheirResponses() {
        val expected =
            mapOf(
                "/api/games/{id}/players/{pid}/confirm" to Triple("post", "confirmLocation", setOf("200", "400", "404", "409")),
                "/api/config" to Triple("get", "getConfig", setOf("200")),
                "/api/games" to Triple("post", "createGame", setOf("201", "400")),
                "/api/games/{id}/players" to Triple("post", "joinGame", setOf("201", "400", "404", "409")),
                "/api/games/{id}/start" to Triple("post", "startGame", setOf("200", "400", "403", "404", "409")),
                "/api/games/{id}" to Triple("get", "getGame", setOf("200", "400", "404")),
                "/api/games/{id}/players/{pid}/location" to Triple("put", "updateLocation", setOf("204", "400", "404")),
            )
        val paths = specification().path("paths")
        val preparationOperations = setOf("getConfig", "createGame", "joinGame", "startGame")
        assertEquals(expected.keys, paths.propertyNames().toSet())
        val operationIds =
            expected.map { (path, contract) ->
                val (method, operationId, responses) = contract
                assertEquals(setOf(method), paths.path(path).propertyNames().toSet())
                val operation = paths.path(path).path(method)
                assertEquals(operationId, operation.path("operationId").asString())
                val tag = if (operationId in preparationOperations) "ゲームの準備" else "プレイ中"
                assertEquals(setOf(tag), names(operation.path("tags")))
                assertEquals(responses, operation.path("responses").propertyNames().toSet())
                assertFalse(operation.path("summary").asString().isBlank())
                operation.path("operationId").asString()
            }
        assertEquals(7, operationIds.toSet().size)
    }

    @Test
    fun test_getApiDocs_requestInputs_preservesRequiredOptionalAndValidationConstraints() {
        val doc = specification()
        val paths = doc.path("paths")
        assertTrue(
            paths
                .path("/api/games")
                .path("post")
                .path("requestBody")
                .path("required")
                .asBoolean(),
        )
        assertFalse(
            paths
                .path("/api/games/{id}/players")
                .path("post")
                .path("requestBody")
                .path("required")
                .asBoolean(false),
        )
        assertEquals(setOf("lat", "lng"), names(schema(doc, "ConfirmLocationRequest").path("required")))
        assertEquals("date-time", schema(doc, "ConfirmLocationResponse").at("/properties/confirmedAt/format").asString())
        assertEquals("uuid", schema(doc, "ConfirmLocationResponse").at("/properties/playerId/format").asString())
        for ((field, bound) in mapOf("lat" to 90.0, "lng" to 180.0)) {
            val coordinate = schema(doc, "ConfirmLocationRequest").path("properties").path(field)
            assertEquals(-bound, coordinate.path("minimum").asDouble())
            assertEquals(bound, coordinate.path("maximum").asDouble())
        }
        val create = schema(doc, "CreateGameRequest")
        assertEquals(setOf("objectType", "areaPreset", "playerCount"), names(create.path("required")))
        assertEquals(setOf("shrine"), names(create.at("/properties/objectType/enum")))
        assertEquals(setOf("small", "medium", "large"), names(create.at("/properties/areaPreset/enum")))
        assertEquals(3, create.at("/properties/playerCount/minimum").asInt())
        assertTrue(allowsNull(create.at("/properties/displayName")))
        assertTrue(allowsNull(schema(doc, "JoinGameRequest").at("/properties/displayName")))
        assertTrue(schema(doc, "JoinGameRequest").path("required").isMissingNode)
        assertEquals(setOf("playerId"), names(schema(doc, "StartGameRequest").path("required")))
        assertEquals(setOf("lat", "lng"), names(schema(doc, "UpdateLocationRequest").path("required")))
        for ((field, bound) in mapOf("lat" to 90.0, "lng" to 180.0)) {
            val coordinate = schema(doc, "UpdateLocationRequest").path("properties").path(field)
            assertEquals(-bound, coordinate.path("minimum").asDouble())
            assertEquals(bound, coordinate.path("maximum").asDouble())
        }
    }

    @Test
    fun test_getApiDocs_responseFields_preservesPresenceNullabilityAndFormats() {
        val doc = specification()
        for (name in listOf(
            "ConfirmLocationResponse",
            "ConfigResponse",
            "AreaPresetPayload",
            "CreateGameResponse",
            "JoinGameResponse",
            "StartGameResponse",
            "GameStateResponse",
            "PlayerPayload",
            "LiveLocationPayload",
            "CurrentAreaPayload",
        )) {
            val model = schema(doc, name)
            assertEquals(model.path("properties").propertyNames().toSet(), names(model.path("required")), name)
        }
        for (name in listOf("creatorPlayerId", "currentArea")) {
            assertTrue(allowsNull(schema(doc, "GameStateResponse").path("properties").path(name)), name)
        }
        assertTrue(allowsNull(schema(doc, "PlayerPayload").at("/properties/live")))
        assertEquals("null", schema(doc, "GameStateResponse").at("/properties/result/type").asString())
        assertEquals("date-time", schema(doc, "LiveLocationPayload").at("/properties/at/format").asString())
        for (name in listOf("CreateGameResponse", "JoinGameResponse", "StartGameResponse", "GameStateResponse", "PlayerPayload")) {
            val properties = schema(doc, name).path("properties")
            for (id in listOf("gameId", "playerId", "creatorPlayerId")) {
                if (properties.has(id)) assertEquals("uuid", properties.path(id).path("format").asString(), "$name.$id")
            }
        }
        for (path in doc.path("paths")) {
            for (operation in path) {
                for (parameter in operation.path("parameters")) {
                    assertTrue(parameter.path("required").asBoolean())
                    assertEquals("uuid", parameter.at("/schema/format").asString())
                }
            }
        }
        val hull = schema(doc, "CurrentAreaPayload").at("/properties/hull")
        assertEquals("array", hull.path("type").asString())
        assertEquals("array", hull.at("/items/type").asString())
        assertEquals("number", hull.at("/items/items/type").asString())
        assertTrue(hull.path("description").asString().contains("[lat, lng]"))
        assertTrue(schema(doc, "CurrentAreaPayload").at("/properties/sqm/description").asString().contains("m²"))
    }

    @Test
    fun test_getApiDocs_successResponses_preservesJsonAndBodyless204() {
        val doc = specification()
        for (path in doc.path("paths")) {
            for (operation in path) {
                val responses = operation.path("responses")
                for (status in listOf("200", "201")) {
                    if (responses.has(status)) {
                        assertEquals(
                            setOf("application/json"),
                            responses
                                .path(status)
                                .path("content")
                                .propertyNames()
                                .toSet(),
                        )
                        val ref = responses.path(status).at("/content/application~1json/schema/$" + "ref").asString()
                        assertFalse(doc.at(ref.removePrefix("#")).isMissingNode)
                    }
                }
            }
        }
        assertFalse(
            doc
                .path("paths")
                .path("/api/games/{id}/players/{pid}/location")
                .at("/put/responses/204")
                .has("content"),
        )
    }

    @Test
    fun test_getApiDocs_errors_distinguishesFrameworkJsonAndBusinessProblemDetails() {
        val doc = specification()
        val paths = doc.path("paths")
        val create400 = paths.path("/api/games").at("/post/responses/400/content")
        assertEquals(setOf("application/json", "application/problem+json"), create400.propertyNames().toSet())
        val join400 = paths.path("/api/games/{id}/players").at("/post/responses/400/content")
        assertEquals(setOf("application/json"), join400.propertyNames().toSet())
        assertEquals(
            setOf("application/problem+json"),
            paths
                .path("/api/games/{id}/players")
                .at("/post/responses/409/content")
                .propertyNames()
                .toSet(),
        )
        val validationRef = create400.path("application/problem+json").at("/schema/$" + "ref").asString()
        val validation = doc.at(validationRef.removePrefix("#"))
        assertTrue(validation.path("properties").has("reason"))
        assertTrue(names(validation.path("required")).contains("reason"))
        val problemRef =
            paths
                .path(
                    "/api/games/{id}/players",
                ).at("/post/responses/409/content/application~1problem+json/schema/$" + "ref")
                .asString()
        assertFalse(doc.at(problemRef.removePrefix("#")).path("properties").has("reason"))
        val formatRef = join400.path("application/json").at("/schema/$" + "ref").asString()
        assertEquals(
            setOf("timestamp", "status", "error", "path"),
            doc
                .at(formatRef.removePrefix("#"))
                .path("properties")
                .propertyNames()
                .toSet(),
        )
    }

    @Test
    fun test_getSwaggerUi_existingApi_redirectsToUiAndLoadsGeneratedDefinition() {
        mockMvc.get("/swagger-ui.html").andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/swagger-ui/index.html")
        }
        mockMvc.get("/v3/api-docs/swagger-config").andExpect {
            status { isOk() }
            jsonPath("$.url") { value("/v3/api-docs") }
        }
    }

    @Test
    fun test_postPlayers_withoutBody_acceptsUnspecifiedDisplayName() {
        val gameId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        whenever(joinGameUseCase.join(any())).thenReturn(JoinGameResult(playerId))

        mockMvc.post("/api/games/$gameId/players").andExpect {
            status { isCreated() }
            jsonPath("$.playerId") { value(playerId.toString()) }
        }
        val input = argumentCaptor<JoinGameInput>()
        verify(joinGameUseCase).join(input.capture())
        assertEquals(gameId, input.firstValue.gameId)
        assertEquals(null, input.firstValue.displayName)
    }

    @Test
    fun test_getGame_nullableFields_serializesPresentNullProperties() {
        val gameId = UUID.randomUUID()
        whenever(getGameStateUseCase.getState(any())).thenReturn(
            GetGameStateResult(gameId, GameStatus.WAITING, 3, null, emptyList(), 500000.0),
        )

        val response =
            mockMvc
                .get("/api/games/$gameId")
                .andExpect { status { isOk() } }
                .andReturn()
                .response
        val body = JsonMapper().readTree(response.contentAsString)
        for (field in listOf("creatorPlayerId", "currentArea", "result")) {
            assertTrue(body.has(field), field)
            assertTrue(body.path(field).isNull, field)
        }
    }

    @Test
    fun test_createGame_invalidBusinessInput_returnsDocumentedProblemWithReason() {
        whenever(createGameUseCase.create(any())).thenThrow(
            GameValidationException(GameValidationException.Reason.INVALID_PLAYER_COUNT, "人数が不正です"),
        )

        mockMvc
            .post("/api/games") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"objectType":"shrine","areaPreset":"small","playerCount":2}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.status") { value(400) }
                jsonPath("$.reason") { value("INVALID_PLAYER_COUNT") }
                jsonPath("$.detail") { value("人数が不正です") }
            }
    }

    private fun specification(): JsonNode =
        JsonMapper().readTree(
            mockMvc
                .get("/v3/api-docs")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun schema(
        doc: JsonNode,
        name: String,
    ): JsonNode = doc.path("components").path("schemas").path(name)

    private fun names(node: JsonNode): Set<String> =
        node
            .iterator()
            .asSequence()
            .map { it.asString() }
            .toSet()

    private fun allowsNull(node: JsonNode): Boolean {
        val type = node.path("type")
        return (type.isString && type.asString() == "null") ||
            (type.isArray && names(type).contains("null")) ||
            node.path("oneOf").any { allowsNull(it) } ||
            node.path("anyOf").any { allowsNull(it) }
    }
}
