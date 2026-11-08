package com.github.gaomond.topo.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 静的ホストのフロントエンドから API を呼べるようにする。
 * 認証情報は許可せず、許可オリジンは app.cors.allowed-origins で指定する。
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
