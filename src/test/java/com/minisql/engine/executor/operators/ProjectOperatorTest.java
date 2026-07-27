package com.minisql.engine.executor.operators;

import com.minisql.engine.binder.BoundColumn;
import com.minisql.storage.Row;
import com.minisql.types.IntegerType;
import com.minisql.types.TextType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectOperatorTest {

    private Row row() {
        Row r = new Row();
        r.set("id", 1);
        r.set("name", "alice");
        r.set("score", 9.5);
        return r;
    }

    @Test
    void keepsOnlyProjectedColumns() {
        FakeOperator child = new FakeOperator(List.of(row()));
        ProjectOperator project = new ProjectOperator(child, List.of(
            new BoundColumn("id", IntegerType.INSTANCE, 0),
            new BoundColumn("name", TextType.INSTANCE, 1)
        ));

        project.open();
        Row result = project.next();

        assertThat(result.getColumnNames()).containsExactlyInAnyOrder("id", "name");
        assertThat(result.getInt("id")).isEqualTo(1);
        assertThat(result.getString("name")).isEqualTo("alice");
        assertThat(result.hasColumn("score")).isFalse();
    }

    @Test
    void returnsNullWhenChildExhausted() {
        FakeOperator child = new FakeOperator(List.of());
        ProjectOperator project = new ProjectOperator(child, List.of());

        project.open();
        assertThat(project.next()).isNull();
    }
}
