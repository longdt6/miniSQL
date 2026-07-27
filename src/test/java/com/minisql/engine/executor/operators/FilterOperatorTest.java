package com.minisql.engine.executor.operators;

import com.minisql.engine.binder.BoundExpression;
import com.minisql.storage.Row;
import com.minisql.types.IntegerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilterOperatorTest {

    private Row row(int id) {
        Row r = new Row();
        r.set("id", id);
        return r;
    }

    @Test
    void emitsOnlyRowsMatchingPredicate() {
        FakeOperator child = new FakeOperator(List.of(row(1), row(2), row(3)));
        BoundExpression predicate = BoundExpression.binary(
            BoundExpression.columnRef("id", IntegerType.INSTANCE, 0),
            ">", new BoundExpression.Literal(1, IntegerType.INSTANCE), IntegerType.INSTANCE);

        FilterOperator filter = new FilterOperator(child, predicate);
        filter.open();

        assertThat(filter.next().getInt("id")).isEqualTo(2);
        assertThat(filter.next().getInt("id")).isEqualTo(3);
        assertThat(filter.next()).isNull();
        filter.close();
    }

    @Test
    void emitsNothingWhenNoRowsMatch() {
        FakeOperator child = new FakeOperator(List.of(row(1)));
        BoundExpression predicate = BoundExpression.binary(
            BoundExpression.columnRef("id", IntegerType.INSTANCE, 0),
            ">", new BoundExpression.Literal(100, IntegerType.INSTANCE), IntegerType.INSTANCE);

        FilterOperator filter = new FilterOperator(child, predicate);
        filter.open();

        assertThat(filter.next()).isNull();
    }

    @Test
    void openAndCloseDelegateToChild() {
        FakeOperator child = new FakeOperator(List.of());
        BoundExpression predicate = new BoundExpression.Literal(true, null);
        FilterOperator filter = new FilterOperator(child, predicate);

        filter.open();
        assertThat(child.isOpened()).isTrue();
        filter.close();
        assertThat(child.isOpened()).isFalse();
    }
}
