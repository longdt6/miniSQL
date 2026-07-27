package com.minisql.engine.executor.operators;

import com.minisql.storage.*;
import com.minisql.types.IntegerType;
import com.minisql.types.TextType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableScanOperatorTest {

    @Test
    void readsAllLiveRowsFromHeapFile(@TempDir Path dir) throws IOException {
        Path heapPath = dir.resolve("users.dat");
        TableMetadata table = new TableMetadata(0, "users", heapPath.toString(), List.of(
            new ColumnMetadata("id", IntegerType.INSTANCE, 0),
            new ColumnMetadata("name", TextType.INSTANCE, 1)
        ));
        TupleDesc desc = TupleDesc.fromTable(table);

        HeapFile hf = new HeapFile(heapPath, "users");
        Page page = hf.readPage(hf.appendPage());

        Row row1 = new Row();
        row1.set("id", 1);
        row1.set("name", "alice");
        page.insert(TupleSerializer.serialize(row1, desc));

        Row row2 = new Row();
        row2.set("id", 2);
        row2.set("name", "bob");
        page.insert(TupleSerializer.serialize(row2, desc));

        hf.writePage(page);

        TableScanOperator scan = new TableScanOperator(table, new BufferPool(4));
        scan.open();

        Row first = scan.next();
        Row second = scan.next();

        assertThat(first.getInt("id")).isEqualTo(1);
        assertThat(first.getString("name")).isEqualTo("alice");
        assertThat(second.getInt("id")).isEqualTo(2);
        assertThat(second.getString("name")).isEqualTo("bob");
        assertThat(scan.next()).isNull();

        scan.close();
    }

    @Test
    void emptyTableReturnsNoRows(@TempDir Path dir) throws IOException {
        Path heapPath = dir.resolve("empty.dat");
        new HeapFile(heapPath, "empty"); // creates the underlying file
        TableMetadata table = new TableMetadata(0, "empty", heapPath.toString(), List.of(
            new ColumnMetadata("id", IntegerType.INSTANCE, 0)
        ));

        TableScanOperator scan = new TableScanOperator(table, new BufferPool(4));
        scan.open();

        assertThat(scan.next()).isNull();
    }
}
