package com.minisql.storage;

import com.minisql.common.Constants;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PageTest {

    private byte[] tuple(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void insertAndGetTuple() {
        Page page = new Page("t.dat", 0);
        int slot = page.insert(tuple("hello"));
        assertThat(slot).isEqualTo(0);
        assertThat(page.getTuple(0)).isEqualTo(tuple("hello"));
        assertThat(page.getNumSlots()).isEqualTo(1);
        assertThat(page.isDirty()).isTrue();
    }

    @Test
    void insertMultipleTuplesGetsSequentialSlots() {
        Page page = new Page("t.dat", 0);
        assertThat(page.insert(tuple("a"))).isEqualTo(0);
        assertThat(page.insert(tuple("b"))).isEqualTo(1);
        assertThat(page.insert(tuple("c"))).isEqualTo(2);
        assertThat(page.getTuple(1)).isEqualTo(tuple("b"));
    }

    @Test
    void hasSpaceReflectsRemainingCapacity() {
        Page page = new Page("t.dat", 0);
        assertThat(page.hasSpace(100)).isTrue();
        assertThat(page.hasSpace(Constants.PAGE_SIZE)).isFalse();
    }

    @Test
    void insertThrowsWhenOutOfSpace() {
        Page page = new Page("t.dat", 0);
        byte[] huge = new byte[Constants.PAGE_SIZE];
        assertThat(page.hasSpace(huge.length)).isFalse();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> page.insert(huge));
    }

    @Test
    void deleteTupleMarksSlotDeleted() {
        Page page = new Page("t.dat", 0);
        page.insert(tuple("x"));
        assertThat(page.isSlotDeleted(0)).isFalse();
        page.deleteTuple(0);
        assertThat(page.isSlotDeleted(0)).isTrue();
    }

    @Test
    void updateTupleInPlaceWhenSameOrSmallerSize() {
        Page page = new Page("t.dat", 0);
        page.insert(tuple("hello"));
        page.updateTuple(0, tuple("world"));
        assertThat(page.getTuple(0)).isEqualTo(tuple("world"));
        assertThat(page.getNumSlots()).isEqualTo(1);
    }

    @Test
    void updateTupleGrowsIntoNewSlotWhenLarger() {
        Page page = new Page("t.dat", 0);
        page.insert(tuple("hi"));
        page.updateTuple(0, tuple("a much longer replacement value"));
        // Old slot becomes a tombstone, new tuple lives in a fresh slot
        assertThat(page.isSlotDeleted(0)).isTrue();
        assertThat(page.getNumSlots()).isEqualTo(2);
        assertThat(page.getTuple(1)).isEqualTo(tuple("a much longer replacement value"));
    }

    @Test
    void compactRemovesDeletedSlotsAndRemapsIndices() {
        Page page = new Page("t.dat", 0);
        page.insert(tuple("a"));
        page.insert(tuple("b"));
        page.insert(tuple("c"));
        page.deleteTuple(1);

        int[] mapping = page.compact();

        assertThat(mapping[0]).isEqualTo(0);
        assertThat(mapping[1]).isEqualTo(-1);
        assertThat(mapping[2]).isEqualTo(1);
        assertThat(page.getNumSlots()).isEqualTo(2);
        assertThat(page.getLiveTupleCount()).isEqualTo(2);
        assertThat(page.getTuple(0)).isEqualTo(tuple("a"));
        assertThat(page.getTuple(1)).isEqualTo(tuple("c"));
    }

    @Test
    void rawDataRoundTripsThroughPageConstructor() {
        Page page = new Page("t.dat", 3);
        page.insert(tuple("persisted"));
        byte[] raw = page.getRawData();

        Page reloaded = new Page("t.dat", 3, raw);
        assertThat(reloaded.getPageNumber()).isEqualTo(3);
        assertThat(reloaded.getTuple(0)).isEqualTo(tuple("persisted"));
        assertThat(reloaded.isDirty()).isFalse();
    }

    @Test
    void constructorRejectsWrongSizedBuffer() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new Page("t.dat", 0, new byte[10]));
    }
}
