package com.eeseka.lynk.support

import org.springframework.boot.test.context.TestComponent
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList

/**
 * Empties the application's tables between tests.
 *
 * Every test class shares the container and the context, so without this a hangout arranged
 * by one test is still there for the next one. The tables are looked up rather than listed, so a new
 * table is cleaned the day it is migrated in. Flyway's own history lives in `public` and is left
 * alone: the schema is migrated once per run, not once per test.
 */
@TestComponent
class DatabaseCleaner(private val jdbcTemplate: JdbcTemplate) {

    fun clear() {
        val tables = jdbcTemplate.queryForList<String>(
            """
            SELECT table_schema || '.' || table_name
            FROM information_schema.tables
            WHERE table_type = 'BASE TABLE'
            AND table_schema NOT IN ('public', 'information_schema')
            AND table_schema NOT LIKE 'pg_%'
            """.trimIndent()
        )

        if (tables.isEmpty()) return

        jdbcTemplate.execute("TRUNCATE TABLE ${tables.joinToString(", ")} RESTART IDENTITY CASCADE")
    }
}
