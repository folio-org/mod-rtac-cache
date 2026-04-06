# AGENTS.md

## What this module does
- `mod-rtac-cache` caches RTAC holdings snapshots in Postgres (`rtac_holding`) to avoid recomputing availability for large instance/item sets.
- Runtime style is API-first + event-driven: REST reads/writes cache, Kafka keeps cache synchronized, scheduled job purges stale rows.

## Architecture map (start here)
- Entry point: `src/main/java/org/folio/rtaccache/RtacCacheApplication.java` (`@EnableCaching`, `@EnableScheduling`).
- REST surface: `src/main/resources/swagger.api/mod-rtac-cache.yaml`; controller implements generated `RtacApi` in `src/main/java/org/folio/rtaccache/rest/RtacCacheController.java`.
- Read path: controller -> `RtacHoldingStorageService` -> `RtacHoldingRepository` native SQL (`rtac_holdings_multi_tenant(...)`) -> DTOs.
- Lazy-load path: `RtacHoldingStorageService` calls `RtacHoldingLazyLoadingService`; cache is generated only for missing instances.
- Generation path: `RtacCacheGenerationService` fetches instance/holdings/items/pieces and bulk-upserts `RtacHoldingEntity` records.

## Data and tenancy model
- Core table is JSONB-centric: `rtac_holding_json` stores shape used by API; many updates are JSONB patches in `RtacHoldingBulkRepository`.
- Multi-tenant/ECS reads depend on DB function `rtac_holdings_multi_tenant` (`src/main/resources/db/changelog/changes/create-rtac-holdings-multi-tenant-function.sql`).
- Stale cleanup uses DB function `delete_old_holdings_all_tenants` and scheduler `RtacCacheInvalidationService.invalidateOldHoldingEntries()`.
- ECS branching is explicit: `RtacHoldingStorageService` uses `EcsUtil` + `ConsortiaService.isCentralTenant()` to choose normal vs ECS lazy-load.

## Event processing pattern
- Kafka entrypoint is `src/main/java/org/folio/rtaccache/integration/KafkaMessageListener.java`.
- Dispatch pattern is `KafkaMessageListener` -> `EventHandlerFactory` -> specific handler in `service/handler/impl`.
- Handlers are keyed by event type + entity type (`getEventType()`, `getEntityType()`), so add both when introducing new event support.
- Example movement logic: `ItemUpdateEventHandler` detects holding moves and calls bulk SQL move/update methods.
- Instance updates in central tenant fan out to member tenants via `ConsortiaService.getConsortiaTenants()`.

## External integrations
- HTTP clients are declarative `@HttpExchange` interfaces in `src/main/java/org/folio/rtaccache/client` and wired in `HttpClientConfiguration`.
- Primary dependencies: inventory, circulation, orders pieces, user-tenants, consortia, consortium-search (see `descriptors/ModuleDescriptor-template.json`).
- System-user tenant scoping is required in async/event code (`SystemUserScopedExecutionService`).
- Kafka topic/group patterns are in `src/main/resources/application.yml` under `folio.kafka.listener.*`.

## Build, test, and generated code workflow
- Standard full build: `mvn clean verify` (also used by release prep in `pom.xml`).
- Surefire is intentionally split in `pom.xml`:
  - default execution runs `*Test`/`*Tests` and excludes `*EcsIT`.
  - execution `non-ecs-it` runs `*IT` excluding `*EcsIT` in isolated fork.
  - execution `ecs-it` runs only `*EcsIT` in separate isolated fork.
- Run only one IT lane via Maven execution ids: `mvn surefire:test@non-ecs-it` or `mvn surefire:test@ecs-it`.
- Integration test base classes (`BaseIntegrationTest`, `BaseEcsIntegrationTest`) bootstrap tenant APIs with MockMvc, WireMock, and Testcontainers Postgres.
- OpenAPI sources are generated to `target/generated-sources`; change `src/main/resources/swagger.api/**` and `RtacCacheController`, not generated files.

## Codebase-specific conventions to follow
- Prefer bulk SQL updates in `RtacHoldingBulkRepository` for high-volume event updates; avoid row-by-row JPA writes in hot paths.
- Keep async boundaries explicit with `applicationTaskExecutor` + `CompletableFuture` joins where ordering matters (`RtacCacheGenerationService`).
- Use `tools.jackson.*` imports (Jackson 3 toolchain) consistently with existing code.
- Keep tenant context propagation explicit when crossing threads or tenants; many failures are tenant-context bugs, not business logic bugs.

