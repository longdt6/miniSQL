package com.minisql.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataTypeTest {

    @ParameterizedTest
    @CsvSource({
        "INT, com.minisql.types.IntegerType",
        "INTEGER, com.minisql.types.IntegerType",
        "int, com.minisql.types.IntegerType",
        "FLOAT, com.minisql.types.FloatType",
        "DOUBLE, com.minisql.types.FloatType",
        "REAL, com.minisql.types.FloatType",
        "TEXT, com.minisql.types.TextType",
        "VARCHAR, com.minisql.types.TextType",
        "STRING, com.minisql.types.TextType",
        "BOOLEAN, com.minisql.types.BooleanType",
        "BOOL, com.minisql.types.BooleanType",
    })
    void fromSqlNameResolvesAliases(String sqlName, String expectedClass) throws ClassNotFoundException {
        DataType resolved = DataType.fromSqlName(sqlName);
        assertThat(resolved).isInstanceOf(Class.forName(expectedClass));
    }

    @Test
    void fromSqlNameThrowsOnUnknownType() {
        assertThatThrownBy(() -> DataType.fromSqlName("NOT_A_TYPE"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
