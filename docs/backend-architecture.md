# Backend architecture

Finals Compass is maintained as a modular monolith. Business modules share one Spring Boot
process and one database deployment, but source dependencies follow explicit module boundaries.
Splitting deployment units is a later operational decision, not a prerequisite for clean code.

## Request lifecycle

The intended request order is:

1. reverse proxy limits, TLS and static assets;
2. servlet CORS and security headers;
3. `RequestTraceFilter`, which creates immutable request metadata and MDC correlation values;
4. authentication;
5. authorization;
6. module-maintenance policy;
7. Spring MVC binding, conversion and Jakarta validation;
8. HTTP controller adapter;
9. application handler and transaction boundary;
10. domain policy;
11. repository or external gateway;
12. response serialization and server compression;
13. access-log completion.

Filter, Spring Security, MVC interceptor, argument resolver and controller advice are distinct
extension points. Code belongs in the narrowest extension point that owns its concern.

## Module layout

New and migrated business code uses this layout:

```text
<module>/
  application/       use-case handlers and transaction boundaries
  domain/            domain types, policies and repository/gateway ports
  infrastructure/    JDBC, filesystem and remote-service adapters
  interfaces/        optional protocol-specific DTOs and adapters
```

Existing controllers remain under `controller` while URLs are kept stable. They are HTTP adapters
only and delegate to the owning module's application handler. They must not use `JdbcClient`, SQL,
filesystem APIs or remote clients directly.

## Dependency rules

- Controller -> application handler -> domain port <- infrastructure adapter.
- Application code may coordinate domain ports but must not depend on servlet response objects.
- Domain code must not depend on Spring MVC, JDBC rows, JSON or HTTP status types.
- Repositories are named after a business capability; do not introduce a generic base repository.
- External systems use gateway/transport ports, not repositories.
- Request DTO, response DTO, domain model and persistence row are separate when their shapes or
  lifecycles differ. Small read-only projections may be shared deliberately.
- A handler represents a user-visible use case. Do not add pass-through layers merely for symmetry.

## Compatibility policy

Structural migrations preserve existing URLs, methods, JSON field names, status codes, database
schema and file locations. A contract change requires a versioned endpoint or an explicit frontend
migration. Internal classes may move freely once tests protect observable behavior.

## Cross-cutting concerns

- Request metadata is stored as `shared.web.RequestContext`; arbitrary request attribute maps are
  not used.
- Authentication establishes the current user once. Authorization is enforced before the domain
  mutation and will migrate to declarative Spring Security rules.
- Expected failures use the global exception boundary. Middleware must not invent a second error
  schema.
- Logs contain identifiers and timings, never passwords, bearer tokens, API keys or uploaded bodies.
- Normal JSON/text compression is configured at the server/proxy. File downloads and streaming AI
  responses must not be buffered into memory by a custom compression filter.

## Migration sequence

1. shared request, error, security and serialization infrastructure;
2. course navigation and teacher-circle;
3. CET papers, items and asset storage;
4. AI Center runtime, knowledge and provider boundaries;
5. system administration and frontend API modules;
6. architecture checks and end-to-end compatibility verification.

`ModuleBoundaryTest` is the first executable architecture guard. Expand it as each controller is
migrated so that direct persistence dependencies cannot return unnoticed.
