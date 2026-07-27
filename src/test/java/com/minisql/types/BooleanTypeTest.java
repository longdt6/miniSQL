package com.minisql.types;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanTypeTest {

    private final BooleanType type = BooleanType.INSTANCE;

    @Test
    void encodeDecodeTrue() {
        byte[] encoded = type.encode(true);
        assertThat(encoded).containsExactly((byte) 1);
        assertThat(type.decode(encoded, 0, 1)).isEqualTo(true);
    }

    @Test
    void encodeDecodeFalse() {
        byte[] encoded = type.encode(false);
        assertThat(encoded).containsExactly((byte) 0);
        assertThat(type.decode(encoded, 0, 1)).isEqualTo(false);
    }

    @Test
    void compare() {
        assertThat(type.compare(false, true)).isLessThan(0);
        assertThat(type.compare(true, false)).isGreaterThan(0);
        assertThat(type.compare(true, true)).isZero();
    }

    @Test
    void getSizeIsOne() {
        assertThat(type.getSize()).isEqualTo(1);
        assertThat(type.isFixedSize()).isTrue();
    }

    @Test
    void getSqlName() {
        assertThat(type.getSqlName()).isEqualTo("BOOLEAN");
    }

    @Test
    void parseAcceptsTrueAndOne() {
        assertThat(type.parse("TRUE")).isEqualTo(true);
        assertThat(type.parse("true")).isEqualTo(true);
        assertThat(type.parse("1")).isEqualTo(true);
    }

    @Test
    void parseAcceptsFalseAndOther() {
        assertThat(type.parse("FALSE")).isEqualTo(false);
        assertThat(type.parse("0")).isEqualTo(false);
        assertThat(type.parse("nonsense")).isEqualTo(false);
    }
}
