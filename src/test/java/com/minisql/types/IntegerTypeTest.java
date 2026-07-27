package com.minisql.types;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntegerTypeTest {

    private final IntegerType type = IntegerType.INSTANCE;

    @Test
    void encodeDecodeRoundTrip() {
        byte[] encoded = type.encode(42);
        assertThat(encoded).hasSize(4);
        assertThat(type.decode(encoded, 0, 4)).isEqualTo(42);
    }

    @Test
    void encodeDecodeNegativeValue() {
        byte[] encoded = type.encode(-123456);
        assertThat(type.decode(encoded, 0, 4)).isEqualTo(-123456);
    }

    @Test
    void encodeDecodeAtOffset() {
        byte[] buffer = new byte[10];
        byte[] encoded = type.encode(99);
        System.arraycopy(encoded, 0, buffer, 3, 4);
        assertThat(type.decode(buffer, 3, 4)).isEqualTo(99);
    }

    @Test
    void compare() {
        assertThat(type.compare(1, 2)).isLessThan(0);
        assertThat(type.compare(2, 1)).isGreaterThan(0);
        assertThat(type.compare(5, 5)).isZero();
    }

    @Test
    void getSizeIsFour() {
        assertThat(type.getSize()).isEqualTo(4);
        assertThat(type.isFixedSize()).isTrue();
    }

    @Test
    void getSqlName() {
        assertThat(type.getSqlName()).isEqualTo("INTEGER");
    }

    @Test
    void parseTrimsAndParses() {
        assertThat(type.parse(" 42 ")).isEqualTo(42);
        assertThat(type.parse("-7")).isEqualTo(-7);
    }
}
