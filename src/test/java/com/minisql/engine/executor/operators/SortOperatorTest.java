package com.minisql.engine.executor.operators;

import com.minisql.engine.parser.ast.Statement;
import com.minisql.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SortOperatorTest {

    private Row row(int id, String name) {
        Row r = new Row();
        r.set("id", id);
        r.set("name", name);
        return r;
    }

    @Test
    void sortsAscendingByDefault() {
        FakeOperator child = new FakeOperator(List.of(row(3, "c"), row(1, "a"), row(2, "b")));
        SortOperator sort = new SortOperator(child, List.of(new Statement.OrderBy("id")));

        sort.open();

        assertThat(sort.next().getInt("id")).isEqualTo(1);
        assertThat(sort.next().getInt("id")).isEqualTo(2);
        assertThat(sort.next().getInt("id")).isEqualTo(3);
        assertThat(sort.next()).isNull();
    }

    @Test
    void sortsDescendingWhenSpecified() {
        FakeOperator child = new FakeOperator(List.of(row(1, "a"), row(2, "b"), row(3, "c")));
        SortOperator sort = new SortOperator(child, List.of(new Statement.OrderBy("id", false)));

        sort.open();

        assertThat(sort.next().getInt("id")).isEqualTo(3);
        assertThat(sort.next().getInt("id")).isEqualTo(2);
        assertThat(sort.next().getInt("id")).isEqualTo(1);
    }

    @Test
    void isBlockingUntilOpenCompletes() {
        FakeOperator child = new FakeOperator(List.of(row(2, "b"), row(1, "a")));
        SortOperator sort = new SortOperator(child, List.of(new Statement.OrderBy("id")));

        sort.open();
        // child must be fully drained by open(), before any next() call
        assertThat(child.next()).isNull();
        assertThat(sort.next().getInt("id")).isEqualTo(1);
    }
}
