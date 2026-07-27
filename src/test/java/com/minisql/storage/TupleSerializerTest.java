package com.minisql.storage;

import com.minisql.types.BooleanType;
import com.minisql.types.FloatType;
import com.minisql.types.IntegerType;
import com.minisql.types.TextType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TupleSerializerTest {

    private TupleDesc desc() {
        return new TupleDesc(List.of(
            new ColumnMetadata("id", IntegerType.INSTANCE, 0),
            new ColumnMetadata("name", TextType.INSTANCE, 1),
            new ColumnMetadata("score", FloatType.INSTANCE, 2),
            new ColumnMetadata("active", BooleanType.INSTANCE, 3)
        ));
    }

    @Test
    void serializeDeserializeRoundTrip() {
        TupleDesc desc = desc();
        Row row = new Row();
        row.set("id", 1);
        row.set("name", "alice");
        row.set("score", 9.5);
        row.set("active", true);

        byte[] bytes = TupleSerializer.serialize(row, desc);
        Row result = TupleSerializer.deserialize(bytes, desc);

        assertThat(result.getInt("id")).isEqualTo(1);
        assertThat(result.getString("name")).isEqualTo("alice");
        assertThat(result.getFloat("score")).isEqualTo(9.5);
        assertThat(result.getBoolean("active")).isTrue();
    }

    @Test
    void nullColumnsRoundTripAsNull() {
        TupleDesc desc = desc();
        Row row = new Row();
        row.set("id", 1);
        row.set("name", null);
        row.set("score", null);
        row.set("active", false);

        byte[] bytes = TupleSerializer.serialize(row, desc);
        Row result = TupleSerializer.deserialize(bytes, desc);

        assertThat(result.get("id")).isEqualTo(1);
        assertThat(result.get("name")).isNull();
        assertThat(result.get("score")).isNull();
        assertThat(result.get("active")).isEqualTo(false);
    }

    @Test
    void variableLengthLastColumnRoundTrips() {
        // TEXT is the last column and variable-length: length is derived from
        // the total tuple length rather than an explicit next-offset.
        TupleDesc desc = new TupleDesc(List.of(
            new ColumnMetadata("id", IntegerType.INSTANCE, 0),
            new ColumnMetadata("description", TextType.INSTANCE, 1)
        ));
        Row row = new Row();
        row.set("id", 7);
        row.set("description", "a fairly long description string");

        byte[] bytes = TupleSerializer.serialize(row, desc);
        Row result = TupleSerializer.deserialize(bytes, desc);

        assertThat(result.getInt("id")).isEqualTo(7);
        assertThat(result.getString("description")).isEqualTo("a fairly long description string");
    }

    @Test
    void deletedFlagRoundTrips() {
        TupleDesc desc = desc();
        Row row = new Row();
        row.set("id", 1);
        row.set("name", "alice");
        row.set("score", 1.0);
        row.set("active", true);

        byte[] bytes = TupleSerializer.serialize(row, desc);
        assertThat(TupleSerializer.isDeleted(bytes)).isFalse();

        TupleSerializer.markDeleted(bytes);
        assertThat(TupleSerializer.isDeleted(bytes)).isTrue();
    }

    @Test
    void computeSizeMatchesActualSerializedSize() {
        TupleDesc desc = desc();
        Row row = new Row();
        row.set("id", 1);
        row.set("name", "bob");
        row.set("score", 2.5);
        row.set("active", false);

        int computed = TupleSerializer.computeSize(row, desc);
        byte[] actual = TupleSerializer.serialize(row, desc);

        assertThat(computed).isEqualTo(actual.length);
    }
}
