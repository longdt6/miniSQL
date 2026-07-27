package com.minisql.engine.executor.operators;

import com.minisql.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LimitOperatorTest {

    private Row row(int id) {
        Row r = new Row();
        r.set("id", id);
        return r;
    }

    @Test
    void stopsAfterEmittingNRows() {
        FakeOperator child = new FakeOperator(List.of(row(1), row(2), row(3)));
        LimitOperator limit = new LimitOperator(child, 2);

        limit.open();

        assertThat(limit.next().getInt("id")).isEqualTo(1);
        assertThat(limit.next().getInt("id")).isEqualTo(2);
        assertThat(limit.next()).isNull();
    }

    @Test
    void limitLargerThanChildRowsEmitsAllRows() {
        FakeOperator child = new FakeOperator(List.of(row(1)));
        LimitOperator limit = new LimitOperator(child, 10);

        limit.open();

        assertThat(limit.next().getInt("id")).isEqualTo(1);
        assertThat(limit.next()).isNull();
    }

    @Test
    void limitZeroEmitsNoRows() {
        FakeOperator child = new FakeOperator(List.of(row(1)));
        LimitOperator limit = new LimitOperator(child, 0);

        limit.open();

        assertThat(limit.next()).isNull();
    }
}
