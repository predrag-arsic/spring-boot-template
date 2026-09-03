package io.vaullet.template;

import io.vaullet.template.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the application from the IDE with its dependencies supplied by Testcontainers.
 *
 * <p>Start this class instead of {@link TemplateApplication} and you get a throwaway PostgreSQL,
 * migrated by Flyway, with no {@code docker compose up} and nothing to clean up afterwards. It is
 * the fastest path from a fresh clone to a running service, and the same container definition the
 * integration tests use — so "works on my machine" and "passes in CI" mean the same thing.
 */
public final class TestTemplateApplication {

    private TestTemplateApplication() {}

    public static void main(String[] args) {
        SpringApplication.from(TemplateApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(concat(args, "--spring.profiles.active=local"));
    }

    private static String[] concat(String[] args, String extra) {
        var all = new String[args.length + 1];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = extra;
        return all;
    }
}
