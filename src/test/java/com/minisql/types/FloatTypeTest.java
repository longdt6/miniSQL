package com.minisql.types;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FloatTypeTest {

    private final FloatType type = FloatType.INSTANCE;

    @Test
    void encodeDecodeRoundTrip() {
        byte[] encoded = type.encode(3.14159);
        assertThat(encoded).hasSize(8);
        assertThat(type.decode(encoded, 0, 8)).isEqualTo(3.14159);
    }

    @Test
    void encodeDecodeNegativeAndZero() {
        assertThat(type.decode(type.encode(-42.5), 0, 8)).isEqualTo(-42.5);
        assertThat(type.decode(type.encode(0.0), 0, 8)).isEqualTo(0.0);
    }

    @Test
    void compare() {
        assertThat(type.compare(1.0, 2.0)).isLessThan(0);
        assertThat(type.compare(2.0, 1.0)).isGreaterThan(0);
        assertThat(type.compare(1.5, 1.5)).isZero();
    }

    @Test
    void getSizeIsEight() {
        assertThat(type.getSize()).isEqualTo(8);
        assertThat(type.isFixedSize()).isTrue();
    }

    @Test
    void getSqlName() {
        assertThat(type.getSqlName()).isEqualTo("FLOAT");
    }

    @Test
    void parseTrimsAndParses() {
        assertThat(type.parse(" 3.5 ")).isEqualTo(3.5);
    }
}
