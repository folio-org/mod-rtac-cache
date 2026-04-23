# mod-rtac-cache

Copyright (C) 2025 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

mod-rtac-cache is a new implementation of mod-rtac with a cache to boost performance for instances with large numbers of items.

## Architecture

Please see the design document [here](https://folio-org.atlassian.net/wiki/spaces/DD/pages/1190428774/RTAC+Cache).

## API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-rtac-cache).

## Issue tracker

There is also a JIRA for this project [here](https://folio-org.atlassian.net/browse/MODRTACHCE).

## Installing and deployment

### Compiling

Compile with
```shell
mvn clean install
```

## Perf tests (opt-in)

This repo includes a small perf-only test lane (excluded from normal `mvn test` / `mvn verify`) to iterate on RTAC read-path performance locally.

Run:
```shell
mvn -Pperf test
```

Notes:
- Requires Docker (Testcontainers).

Optional knobs:
- `-Dperf.itemsTotal=10000`
- `-Dperf.instanceId=11111111-1111-1111-1111-111111111111`
- `-Dperf.maxGetMs=...` / `-Dperf.maxBatchMs=...` (turn perf measurements into assertions)

### Running mod-rtac-cache

Run locally on listening port 8081 (default listening port):

Using Docker to run the local stand-alone instance:

```shell
DB_HOST=localhost DB_PORT=5432 DB_DATABASE=okapi_modules DB_USERNAME=folio_admin DB_PASSWORD=folio_admin \
   java -Dserver.port=8081 -jar target/mod-rtac-cache-*.jar
```

### Docker

Build the docker container with:

```shell
docker build -t dev.folio/mod-rtac-cache .
```

### Module Descriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### Environment Variables

| Variable | Default                 | Purpose |
|---|-------------------------|---|
| `DB_USERNAME` | `folio_admin`           | DB username |
| `DB_PASSWORD` | `folio_admin`           | DB password |
| `DB_HOST` | `localhost`             | Postgres host |
| `DB_PORT` | `5432`                  | Postgres port |
| `DB_DATABASE` | `db`                    | Postgres database name |
| `KAFKA_HOST` | `localhost`             | Kafka broker host |
| `KAFKA_PORT` | `9092`                  | Kafka broker port |
| `MAX_POLL_RECORDS` | `50`                    | Kafka consumer max poll size |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT`             | Kafka security protocol |
| `KAFKA_SSL_KEYSTORE_PASSWORD` | *(empty)*               | Kafka SSL keystore password |
| `KAFKA_SSL_KEYSTORE_LOCATION` | *(empty)*               | Kafka SSL keystore path |
| `KAFKA_SSL_TRUSTSTORE_PASSWORD` | *(empty)*               | Kafka SSL truststore password |
| `KAFKA_SSL_TRUSTSTORE_LOCATION` | *(empty)*               | Kafka SSL truststore path |
| `ENV` | `folio`                 | FOLIO environment name |
| `OKAPI_URL` | `http://localhost:8081` | Okapi base URL |
| `KAFKA_RETRY_INTERVAL_MS` | `2000`                  | Retry interval |
| `KAFKA_RETRY_DELIVERY_ATTEMPTS` | `6`                     | Retry attempts |
| `KAFKA_EVENTS_CONCURRENCY` | `2`                     | Kafka listener concurrency |
| `KAFKA_EVENTS_CONSUMER_PATTERN` | computed per topic      | Kafka topic pattern override |
| `SYSTEM_USER_ENABLED` | `false`                 | Enable system user |
| `SYSTEM_USER_USERNAME` | `mod-rtac-cache`        | System username |
| `SYSTEM_USER_PASSWORD` | *(none)*                | System password (required when enabled) |
| `RTAC_CACHE_INVALIDATION_CRON` | `0 0 2 * * ?`           | Cache invalidation schedule |
| `RTAC_CACHE_RETENTION_DAYS` | `2`                     | Cache retention days |
