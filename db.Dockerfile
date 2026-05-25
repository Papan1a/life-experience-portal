# docker/db.Dockerfile
# PostgreSQL 16 + pg_uuidv7 extension (S9).
# NOTE: pin PG_UUIDV7_REF to a release tag/commit and verify the build at implementation.
FROM postgres:16

ARG PG_UUIDV7_REF=main

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        build-essential git ca-certificates postgresql-server-dev-16; \
    git clone --depth 1 --branch "${PG_UUIDV7_REF}" \
        https://github.com/fboulnois/pg_uuidv7.git /tmp/pg_uuidv7; \
    cd /tmp/pg_uuidv7; \
    make; \
    make install; \
    rm -rf /tmp/pg_uuidv7; \
    apt-get purge -y --auto-remove build-essential git postgresql-server-dev-16; \
    rm -rf /var/lib/apt/lists/*

# The extension is CREATEd by Flyway V1 (CREATE EXTENSION IF NOT EXISTS pg_uuidv7).
# Migrations must run as a role allowed to create extensions (e.g. the superuser).
