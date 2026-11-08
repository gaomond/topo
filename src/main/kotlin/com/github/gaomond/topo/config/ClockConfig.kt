package com.github.gaomond.topo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** 在室判定の基準時刻をテストで固定できるよう、時刻源を注入する。本番は UTC 実時刻。 */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
