package com.minisql.engine.binder;

import com.minisql.types.DataType;

public record BoundColumn(String name, DataType dataType, int columnIndex) {}
