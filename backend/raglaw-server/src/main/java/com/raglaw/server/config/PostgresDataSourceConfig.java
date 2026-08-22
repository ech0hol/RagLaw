package com.raglaw.server.config;

import com.raglaw.rag.config.RagProperties;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(prefix = "raglaw.rag.postgres", name = "enabled", havingValue = "true")
public class PostgresDataSourceConfig {

    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource(RagProperties ragProperties) {
        RagProperties.Postgres postgres = ragProperties.getPostgres();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setMaximumPoolSize(5);
        dataSource.setPoolName("raglaw-postgres");
        return dataSource;
    }

    @Bean(name = "postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
