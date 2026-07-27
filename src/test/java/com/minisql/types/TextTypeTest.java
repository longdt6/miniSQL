package com.minisql.types;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTypeTest {

    private final TextType type = TextType.INSTANCE;

    @Test
    void encodeDecodeRoundTrip() {
        byte[] encoded = type.encode("hello");
        assertThat(type.decode(encoded, 0, encoded.length)).isEqualTo("hello");
    }

    @Test
    void encodeDecodeEmptyString() {
        byte[] encoded = type.encode("");
        assertThat(encoded).hasSize(2);
        assertThat(type.decode(encoded, 0, encoded.length)).isEqualTo("");
    }

    @Test
    void encodeDecodeUnicode() {
        byte[] encoded = type.encode("héllo wörld");
        assertThat(type.decode(encoded, 0, encoded.length)).isEqualTo("héllo wörld");
    }

    @Test
    void compare() {
        assertThat(type.compare("a", "b")).isLessThan(0);
        assertThat(type.compare("b", "a")).isGreaterThan(0);
        assertThat(type.compare("a", "a")).isZero();
    }

    @Test
    void variableSize() {
        assertThat(type.getSize()).isEqualTo(-1);
        assertThat(type.isFixedSize()).isFalse();
    }

    @Test
    void getSqlName() {
        assertThat(type.getSqlName()).isEqualTo("TEXT");
    }

    @Test
    void parseStripsQuotes() {
        assertThat(type.parse("'hello'")).isEqualTo("hello");
    }

    @Test
    void parseHandlesEscapedQuotes() {
        assertThat(type.parse("'it''s'")).isEqualTo("it's");
    }

    @Test
    void parseWithoutQuotesReturnsTrimmed() {
        assertThat(type.parse("plain")).isEqualTo("plain");
    }
}
