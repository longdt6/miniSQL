package com.minisql;

import com.minisql.common.Constants;
import com.minisql.engine.SqlEngine;
import com.minisql.storage.BufferPool;
import com.minisql.storage.Catalog;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class MiniSqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniSqlApplication.class, args);
    }

    @Bean
    public Catalog catalog(@Value("${minisql.data-dir:data/" + Constants.DEFAULT_DB_NAME + "}") String dataDir) {
        return new Catalog(dataDir);
    }

    @Bean
    public BufferPool bufferPool() {
        return new BufferPool(200);
    }

    @Bean
    public SqlEngine sqlEngine(Catalog catalog, BufferPool bufferPool) {
        return new SqlEngine(catalog, bufferPool);
    }

    @Component
    static class ShutdownHook {

        private final Catalog catalog;
        private final BufferPool bufferPool;

        ShutdownHook(Catalog catalog, BufferPool bufferPool) {
            this.catalog = catalog;
            this.bufferPool = bufferPool;
        }

        @PreDestroy
        void shutdown() {
            bufferPool.flushAll();
            catalog.close();
        }
    }
}
