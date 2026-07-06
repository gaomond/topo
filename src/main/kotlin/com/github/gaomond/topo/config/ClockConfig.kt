package com.github.gaomond.topo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 時刻源（[Clock]）の DI 設定。
 *
 * presence（在室判定・US-08）は now を要する policy のため、UseCase に [Clock] を注入して
 * テストで境界時刻を固定できるようにする。本番は UTC 実時刻。
 */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
