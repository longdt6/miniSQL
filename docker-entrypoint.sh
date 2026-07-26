#!/bin/sh
exec java -jar app.jar --server.port="${PORT:-8080}" --minisql.data-dir=/app/data
