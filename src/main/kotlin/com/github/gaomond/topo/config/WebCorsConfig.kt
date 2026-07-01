package com.github.gaomond.topo.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CORS 許可設定（01-spec 1.3 / DESIGN 4）。
 *
 * フロント（静的ホスト・別オリジン）から /api 配下を呼べるようにする。認証なしのため
 * Credentials なし・オリジン許可のみ。許可オリジンは `app.cors.allowed-origins`
 * （application.properties）から注入し、ハードコードしない。
 */
@Configuration
class WebCorsConfig(
    @param:Value("\${app.cors.allowed-origins}")
    private val allowedOrigins: List<String>,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
    }
}
