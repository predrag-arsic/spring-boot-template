package io.vaullet.template.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real PostgreSQL for tests, started on demand by Testcontainers.
 *
 * <p>Testing persistence against H2 tests H2. Dialect differences (upsert syntax, {@code TIMESTAMPTZ}
 * semantics, partial and functional indexes, JSONB, case-insensitive collation) are exactly where
 * data-access bugs live, so the integration tests run against the same engine production does —
 * pinned to a digest-free but explicit tag so a silent upstream change cannot alter results.
 *
 * <p>{@code @ServiceConnection} is the piece that removes the boilerplate: Boot reads the container's
 * host, port and credentials and configures the {@code DataSource} itself. No
 * {@code @DynamicPropertySource} block, no property names to keep in sync.
 *
 * <p>The container is a singleton for the whole JVM because Spring caches application contexts
 * between test classes; a per-class container would restart PostgreSQL for every test class and
 * dominate the build time.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }
}
