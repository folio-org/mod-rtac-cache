## 17/04/2026 v1.1.0

* [MODRTACHCE-11](https://folio-org.atlassian.net/browse/MODRTACHCE-11) - Implement ECS for mod-rtac-cache (#22)
* [MODRTACHCE-14](https://folio-org.atlassian.net/browse/MODRTACHCE-14) - Add listeners for Location and Library events
* [MODRTACHCE-15](https://folio-org.atlassian.net/browse/MODRTACHCE-15) - Filter RtacHoldings by instance: return Item/Piece sets or Holding/Piece sets as applicable (#16)
* [MODRTACHCE-17](https://folio-org.atlassian.net/browse/MODRTACHCE-17) - Add sorting to search and browse endpoints (#23)
* [MODRTACHCE-19](https://folio-org.atlassian.net/browse/MODRTACHCE-19) - Add addtional properties to batch summary response (#20)
* [MODRTACHCE-21](https://folio-org.atlassian.net/browse/MODRTACHCE-21) - Implement Cache generation and Kafka listeners logic for Bound With Items
* [MODRTACHCE-22](https://folio-org.atlassian.net/browse/MODRTACHCE-22) - Handle instance update when local instance becomes shared (#28)
* [MODRTACHCE-23](https://folio-org.atlassian.net/browse/MODRTACHCE-23) - Handle pieces in central tenant for ECS (#24)
* [MODRTACHCE-27](https://folio-org.atlassian.net/browse/MODRTACHCE-27) - Handle instance event to populate format ids
* [MODRTACHCE-28](https://folio-org.atlassian.net/browse/MODRTACHCE-28) - Handle reference data events (#41)
* [MODRTACHCE-29](https://folio-org.atlassian.net/browse/MODRTACHCE-29) - handle discovery suppress flag (#30)
* [MODRTACHCE-31](https://folio-org.atlassian.net/browse/MODRTACHCE-31) - Populate Instance format ids during cache generation
* [MODRTACHCE-33](https://folio-org.atlassian.net/browse/MODRTACHCE-33) - fix: adding call number at the holding level is not fetched and displayed on rtac table
* [MODRTACHCE-33](https://folio-org.atlassian.net/browse/MODRTACHCE-33) - fix: populate empty item effective call number value
* [MODRTACHCE-34](https://folio-org.atlassian.net/browse/MODRTACHCE-34) - Fix default sort direction to DESC for /rtac-cache/search/{instanceId} and /rtac-cache/{id} (#44)
* [MODRTACHCE-35](https://folio-org.atlassian.net/browse/MODRTACHCE-35) - fix boolean CQL query
* [MODRTACHCE-35](https://folio-org.atlassian.net/browse/MODRTACHCE-35) - fix pieces kafka event handling
* [MODRTACHCE-36](https://folio-org.atlassian.net/browse/MODRTACHCE-36) - handle call number prefix and suffix (#34)
* [MODRTACHCE-37](https://folio-org.atlassian.net/browse/MODRTACHCE-37) - Ensure instance formats are retrieved for rtac batch request
* [MODRTACHCE-39](https://folio-org.atlassian.net/browse/MODRTACHCE-39) - Refactor update handling to avoid "lost updates" (#37)
* [MODRTACHCE-40](https://folio-org.atlassian.net/browse/MODRTACHCE-40) - MOD-RTAC-CACHE - self evaluation (#39)
* [MODRTACHCE-41](https://folio-org.atlassian.net/browse/MODRTACHCE-41) - handle move holdings/items to another instance (#40)
* [MODRTACHCE-42](https://folio-org.atlassian.net/browse/MODRTACHCE-42) - fix updating bound-with items (#42)
* [MODRTACHCE-45](https://folio-org.atlassian.net/browse/MODRTACHCE-45) - fix creating bound-with items
* [MODRTACHCE-46](https://folio-org.atlassian.net/browse/MODRTACHCE-46) - fix logs spam in Kafka handler

## 11/12/2025 v1.0.0 - Released
Initial release of `mod-rtac-cache` module

### Stories
* [MODRTACHCE-16](https://folio-org.atlassian.net/browse/MODRTACHCE-16) - Create pg_trgm extension globally
* [MODRTACHCE-13](https://folio-org.atlassian.net/browse/MODRTACHCE-13) - Deploy mod-rtac-cache to rancher env
* [MODRTACHCE-12](https://folio-org.atlassian.net/browse/MODRTACHCE-12) - Create pre-warmer endpoint that creates an async job with audit table to track progress
* [MODRTACHCE-10](https://folio-org.atlassian.net/browse/MODRTACHCE-10) - Add listeners for Piece and Circulation Kafka events
* [MODRTACHCE-9](https://folio-org.atlassian.net/browse/MODRTACHCE-9) - Develop method for populating cache
* [MODRTACHCE-8](https://folio-org.atlassian.net/browse/MODRTACHCE-8) - Add RtacHolding mapper from Holding, Piece, Item
* [MODRTACHCE-7](https://folio-org.atlassian.net/browse/MODRTACHCE-7) - Add RTAC search API
* [MODRTACHCE-6](https://folio-org.atlassian.net/browse/MODRTACHCE-6) - Implement RTAC rtac and rtac-batch browse API
* [MODRTACHCE-5](https://folio-org.atlassian.net/browse/MODRTACHCE-5) - Create new FeignClients to fetch data from folio modules
* [MODRTACHCE-4](https://folio-org.atlassian.net/browse/MODRTACHCE-4) - Create RtacHolding Storage
* [MODRTACHCE-3](https://folio-org.atlassian.net/browse/MODRTACHCE-3) - Add listeners for Holdings and Item Kafka events
* [MODRTACHCE-2](https://folio-org.atlassian.net/browse/MODRTACHCE-2) - Create data model for new mod-rtac-cache
* [MODRTACHCE-1](https://folio-org.atlassian.net/browse/MODRTACHCE-1) - Create module skeleton based on FOLIO conventions
