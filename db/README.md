# DocMind — Database init vs. Flyway ownership

`db/init/001_init.sql` runs **once**, only when the Postgres data volume is first
created (via Postgres' `/docker-entrypoint-initdb.d` hook). Its sole job is to
guarantee the `vector` extension exists (`CREATE EXTENSION IF NOT EXISTS vector;`).
All application schema — the `documents`, `blocks`, `chunks`, embedding, and job
tables — is owned by **Flyway** inside the `api-java` service (migrations under
`api-java/src/main/resources/db/migration`). Keep DDL in Flyway only; do **not**
add application tables to the init SQL, or later maintainers will fight duplicate,
drifting definitions across two places.
