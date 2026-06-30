package com.github.gaomond.topo.support

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * テスト用の共有 PostGIS コンテナ。
 *
 * compose.yaml と同じ `imresamu/postgis:17-3.5-bookworm` を用い、実 PostGIS で
 * Flyway マイグレーション・enum/FK 制約・空間型を検証する（Level 3）。
 * コンテナ起動コストを抑えるため static に 1 つだけ起動して全テストで共有する。
 */
object PostgisTestContainer {
    val instance: PostgreSQLContainer<*> =
        PostgreSQLContainer(
            DockerImageName
                .parse("imresamu/postgis:17-3.5-bookworm")
                .asCompatibleSubstituteFor("postgres"),
        ).apply {
            withDatabaseName("topo")
            withUsername("topo")
            withPassword("topo")
            start()
        }
}
