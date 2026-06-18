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
Optional knobs:
- `-Dperf.itemsTotal=10000`
- `-Dperf.instanceId=11111111-1111-1111-1111-111111111111`
- `-Dperf.maxGetMs=...` / `-Dperf.maxSearchMs=...` / `-Dperf.maxBatchMs=...` (turn perf measurements into assertions)

### Running mod-rtac-cache

Run locally on listening port 8081 (default listening port):

Using Docker to run the local stand-alone instance:

```shell
DB_HOST=localhost DB_PORT=5432 DB_DATABASE=okapi_modules DB_USERNAME=folio_admin DB_PASSWORD='<password>' \
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
| `DB_PASSWORD` | *(required)*             | DB password |
| `DB_HOST` | `localhost`             | Postgres host |
| `DB_PORT` | `5432`                  | Postgres port |
| `DB_DATABASE` | `db`                    | Postgres database name |
| `KAFKA_HOST` | `localhost`             | Kafka broker host |
| `KAFKA_PORT` | `9092`                  | Kafka broker port |
| `KAFKA_MAX_POLL_RECORDS` | `50`                    | Kafka consumer max poll size |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT`             | Kafka security protocol |
| `KAFKA_SSL_KEYSTORE_PASSWORD` | *(empty)*               | Kafka SSL keystore password |
| `KAFKA_SSL_KEYSTORE_LOCATION` | *(empty)*               | Kafka SSL keystore path |
| `KAFKA_SSL_TRUSTSTORE_PASSWORD` | *(empty)*               | Kafka SSL truststore password |
| `KAFKA_SSL_TRUSTSTORE_LOCATION` | *(empty)*               | Kafka SSL truststore path |
| `ENV` | `folio`                 | FOLIO environment name |
| `OKAPI_URL` | *(required)*             | Okapi base URL |
| `KAFKA_RETRY_INTERVAL_MS` | `2000`                  | Retry interval |
| `KAFKA_RETRY_DELIVERY_ATTEMPTS` | `6`                     | Retry attempts |
| `KAFKA_EVENTS_CONCURRENCY` | `2`                     | Kafka listener concurrency |
| `KAFKA_EVENTS_CONSUMER_PATTERN` | computed per topic      | Kafka topic pattern override |
| `SYSTEM_USER_ENABLED` | `false`                 | Enable legacy Okapi system-user token retrieval |
| `SYSTEM_USER_USERNAME` | `mod-rtac-cache`        | Legacy Okapi system-user username |
| `SYSTEM_USER_PASSWORD` | *(none)*                | Legacy Okapi system-user password, required only when enabled in classic Okapi deployments |
| `RTAC_CACHE_INVALIDATION_CRON` | `0 0 2 * * ?`           | Cache invalidation schedule |
| `RTAC_CACHE_RETENTION_DAYS` | `2`                     | Cache retention days |

## ASF Category B license notice

Apache's [third-party license policy](https://www.apache.org/legal/resolved.html#category-b) allows
Category B dependencies only under the conditions described there. The dependency license scan for this project reported
the following dependencies whose detected licenses match ASF Category B license families:

| Project | Detected dependency or dependencies | Detected license | Project URL |
|---|---|---|---|
| iStack Common Utility Code | `com.sun.istack:istack-commons-runtime:4.1.2` | `EPL-1.0` | https://github.com/eclipse-ee4j/jaxb-istack-commons |
| Jakarta Activation API | `jakarta.activation:jakarta.activation-api:2.1.4` | `EPL-1.0` | https://github.com/jakartaee/jaf-api |
| Jakarta XML Binding API | `jakarta.xml.bind:jakarta.xml.bind-api:4.0.4` | `EPL-1.0` | https://github.com/jakartaee/jaxb-api |
| AspectJ Weaver | `org.aspectj:aspectjweaver:1.9.25.1` | `Eclipse Public License` | https://www.eclipse.org/aspectj/ |
| Eclipse Angus Activation | `org.eclipse.angus:angus-activation:2.0.3` | `EPL-1.0` | https://github.com/eclipse-ee4j/angus-activation |
| Eclipse Sisu Plexus | `org.eclipse.sisu:org.eclipse.sisu.plexus:0.9.0.M2` | `Eclipse Public License 1.0` | https://www.eclipse.org/sisu/ |
| JAXB Runtime | `org.glassfish.jaxb:jaxb-core:4.0.6`; `org.glassfish.jaxb:jaxb-runtime:4.0.6`; `org.glassfish.jaxb:txw2:4.0.6` | `EPL-1.0` | https://eclipse-ee4j.github.io/jaxb-ri/ |
| JUnit | `org.junit.jupiter:junit-jupiter-api:6.0.3`; `org.junit.jupiter:junit-jupiter-engine:6.0.3`; `org.junit.jupiter:junit-jupiter-params:6.0.3`; `org.junit.platform:junit-platform-commons:6.0.3`; `org.junit.platform:junit-platform-engine:6.0.3`; `org.junit.platform:junit-platform-launcher:6.0.3` | `EPL-2.0` | https://junit.org/ |
| Mozilla Rhino | `org.mozilla:rhino:1.9.1` | `Mozilla Public License 2.0` | https://mozilla.github.io/rhino/ |

