package com.minisql.storage;

import com.minisql.common.SqlException;
import com.minisql.types.IntegerType;
import com.minisql.types.TextType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTest {

    @Test
    void loadsExistingCatalogJson(@TempDir Path dir) throws IOException, SqlException {
        Files.writeString(dir.resolve("catalog.json"), """
                {
                  "tables": [
                    {
                      "id": 1,
                      "name": "users",
                      "heapFile": "data/mydb/users.dat",
                      "columns": [
                        {"name": "id", "type": "INTEGER", "position": 0},
                        {"name": "name", "type": "TEXT", "position": 1},
                        {"name": "age", "type": "INTEGER", "position": 2}
                      ]
                    }
                  ]
                }
                """);

        Catalog catalog = new Catalog(dir.toString());

        TableMetadata table = catalog.getTable("users");
        assertThat(table.tableId()).isEqualTo(1);
        assertThat(table.heapFilePath()).isEqualTo("data/mydb/users.dat");
        assertThat(table.columns()).extracting("name").containsExactly("id", "name", "age");
        assertThat(table.column("age").orElseThrow().dataType()).isEqualTo(IntegerType.INSTANCE);
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path dir) throws Exception {
        Catalog catalog = new Catalog(dir.toString());
        catalog.createTable("people", List.of(
                new Catalog.ColumnDef("id", IntegerType.INSTANCE),
                new Catalog.ColumnDef("name", TextType.INSTANCE)
        ));

        Catalog reloaded = new Catalog(dir.toString());
        TableMetadata table = reloaded.getTable("people");
        assertThat(table.columns()).extracting("name").containsExactly("id", "name");
        assertThat(reloaded.getTableNames()).containsExactly("people");
    }
}
