# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**YOFC-IOT** is an OSGi bundle-based IoT edge gateway platform built on **Eclipse Kura** and **Eclipse Vert.x**. It produces OSGi bundles (`.jar`) and deployable OSGi Deployment Packages (`.dp` files). The platform supports industrial protocols (OPC UA, PLC4X, Modbus, S7, EtherNet/IP), time-series databases (InfluxDB v1/v2), and embedded databases (H2).

- **Language:** Java 17
- **Build:** Apache Maven (multi-module)
- **Runtime:** Apache Felix OSGi / Eclipse Kura gateway
- **Remote:** https://gitee.com/heyoulin/IdeaProjects/yofc-iot

## Build Commands

```bash
# Build all modules (produces .dp files in each module's target/)
mvn clean install

# Build with tests enabled (tests are skipped by default)
mvn clean install -DskipTests=false

# Build a single module
mvn clean install -pl com.yofc.iot.apps/com.yofc.iot.apps.opcuaserver
```

## Running Tests

Tests use **JUnit 5 (Jupiter)**. They are disabled by default in the parent POM.

```bash
# Run all tests
mvn test -DskipTests=false

# Run tests for a specific module
mvn test -pl com.yofc.iot.apps/com.yofc.iot.apps.opcuaserver -DskipTests=false
```

Test files live in `src/test/java/` within each module. Note: many test files are informal demos/diagnostics rather than structured unit tests.

## Module Structure

```
yofc-iot (root, packaging: pom)
├── com.yofc.iot.api/
│   └── com.yofc.iot.api.comp       # Vert.x runtime + event bus bridge (OSGi hosted)
├── com.yofc.iot.apps/
│   ├── com.yofc.iot.apps.comp      # Core: MQTT v3/v5, HTTP REST/proxy, Groovy filter, H2 driver
│   ├── com.yofc.iot.apps.opcuaserver  # OPC UA server + PLC4X multi-protocol driver
│   ├── com.yofc.iot.apps.abplc     # Allen-Bradley PLC via EtherNet/IP (Kura Driver)
│   ├── com.yofc.iot.apps.ot3001    # OT3001 serial device communication
│   ├── com.yofc.iot.apps.visa      # VISA instrument control (GPIB/TCP/Serial)
│   ├── com.yofc.iot.apps.influxdb  # InfluxDB v2 connector
│   └── com.yofc.iot.apps.influxdbv1 # InfluxDB v1 connector
└── com.yofc.iot.repack/
    └── yofc-repo                    # P2 update site for Jersey JAX-RS bundles
```

> `com.yofc.iot.repack.vertx.db` (the repackaged Vert.x SQL clients + JDBC driver bundle) was migrated to the `kura-yofc-runtime` sibling repo and now ships via `kura-yofc-runtime.deb` to `/opt/eclipse/kura/siblings/yofc-runtime/6/`. yofc-iot apps still Import-Package its exported `io.vertx.*` SQL client packages at runtime — no change needed in the apps' MANIFESTs.

## Key Architecture Patterns

### OSGi Declarative Services
All components are defined via SCR XML descriptors in `OSGI-INF/` directories. Components use `configuration-policy="require"` and dynamic binding. The `maven-bundle-plugin` generates MANIFEST.MF and processes `@Component`/`@Metatype` annotations.

### Eclipse Kura Integration
Heavy use of Kura APIs: `Driver`, `WireComponent`/`WireEmitter`/`WireReceiver`, `AssetHelp`, `ConfigurableComponent`, `SslManagerService`, `CryptoService`. Kura API is scoped as `provided` in Maven.

### Vert.x Event Bus
`com.yofc.iot.api.comp` hosts a Vert.x runtime inside OSGi. `VertxVerticleHost` manages verticle lifecycle dynamically (binds/unbinds `Verticle` services at runtime). An event bus bridge enables external communication.

### Industrial Protocols
- **OPC UA:** Eclipse Milo SDK (server + client)
- **PLC4X:** Apache PLC4X (AB-ETH, S7, Modbus, ADS, CANopen, EIP, KNX, Firmata)
- **Custom:** Allen-Bradley EtherNet/IP driver (port 44818)

### Repackaged Dependencies
Historically the `com.yofc.iot.repack.vertx.db` bundle in this module repackaged Vert.x SQL clients with JDBC drivers (PostgreSQL, MySQL, MSSQL, Oracle) into a single OSGi bundle to resolve OSGi dependency issues. As of 2026-05-24 that bundle lives in the `kura-yofc-runtime` sibling (see workspace CLAUDE.md). What's left in `com.yofc.iot.repack/` is only `yofc-repo` (the P2 update site).

## OSGi Component Registry

Components are defined in `OSGI-INF/*.xml` files. Key services:

| Component | Module | Purpose |
|---|---|---|
| `VertxVerticleHost` | api.comp | Dynamic verticle registry |
| `MqttClientServiceImpl` | apps.comp | MQTT v3 client service |
| `MqttV5ClientServiceImpl` | apps.comp | MQTT v5 client service |
| `GroovyScriptFilter` | apps.comp | Wire Admin Groovy script executor |
| `H2MVStoreDriver` | apps.comp | Embedded H2 driver |
| `OPCUAServerServiceImpl` | apps.opcuaserver | OPC UA server (Milo) |
| `Plc4xDriver` | apps.opcuaserver | Multi-protocol PLC driver |
| `ABPlcDriver` | apps.abplc | Allen-Bradley EtherNet/IP |
| `InfluxdbComponent` | apps.influxdb(v1) | Time-series DB connectors |

## Important Conventions

- **Dependencies are `provided` scoped:** OSGi/Kura APIs, H2, Groovy, Lombok, logging, MQTT, OSGi core -- they are expected to be available in the runtime.
- **Embedded JARs:** Bundles embed dependencies via `<Embed-Directory>lib</Embed-Directory>` in the maven-bundle-plugin config.
- **SCR descriptors:** Located in `OSGI-INF/` at the module root (not `src/main/resources`). The plugin exports them via `<exportScr>true</exportScr>`.
- **`.dp` output:** Each module produces a Deployment Package in `target/*.dp` for OSGi deployment.
- **No Docker/K8s:** Deployment target is an OSGi runtime (Apache Felix / Eclipse Kura), not containers.

## Kura Snapshot Behavior (shared host runtime)

yofc-iot bundles are `ConfigurableComponent`s that persist into the same `/opt/eclipse/kura/user/snapshots/snapshot_N.xml` as monorepo + sibling components. Two host-runtime traits to keep in mind when shipping or uninstalling `.dp` packages:

### Orphan entries linger after uninstall

If a `.dp` is uninstalled while its components have configurations in the snapshot, those entries stay in `snapshot_0.xml`. On the next Kura start, monorepo `ConfigurationServiceImpl.loadLatestSnapshotInConfigAdmin()` tries to recreate them, fails to find the `factoryPid`, and logs:

```
WARN  Error creating configuration with pid: <pid> and factory pid: <factoryPid>
```

Kura keeps booting — but the orphans are merged back into every subsequent snapshot, so `snapshot_N.xml` grows unbounded. Cleanup is manual (reinstall the `.dp` → delete via Web UI, or hand-edit `snapshot_0.xml`). This applies to **every** yofc-iot module that registers configurable components (Vertx verticles, MQTT clients, Plc4xDriver, OPC UA server, AB-PLC driver, InfluxDB connectors, …).

When designing a custom wire/orchestration service that references peers by PID, **follow the `WireGraphServiceImpl` pattern** — `getServicePidByKuraServicePid()` returns `null` for missing peers and the calling code silently skips, never throws. Don't NPE the activator.

### Snapshot encryption is config-driven (YOFC fork divergence)

Upstream Eclipse Kura always encrypts `snapshot_N.xml` via `CryptoService`. The YOFC fork (this Kura runtime) reads `kura.snapshots.encrypt` from `/opt/eclipse/kura/framework/kura_custom.properties`:

| Setting | Behavior |
| --- | --- |
| `kura.snapshots.encrypt=true` (default) | Encrypted snapshot, matches upstream |
| `kura.snapshots.encrypt=false` | Plaintext XML on disk |

Practical impact on yofc-iot work:

- **Debugging persisted config**: with encrypt=false you can `cat snapshot_0.xml` to inspect what your component wrote — no master key needed.
- **Sibling reinstall flows**: `kura-networking` / `kura-firewall-only` preinst rejects install once `snapshot_0.xml` is encrypted (first line ≠ `<?xml ...?>`). If yofc-iot starts Kura before networking sibling install, set encrypt=false first, or install networking before first `systemctl start kura`.
- Authoritative reference and reasoning live in monorepo `kura/CLAUDE.md` § *Snapshot 行为* (same project).

# Codebase Memory MCP — Code Intelligence

This project is indexed by **codebase-memory-mcp**. Always use it BEFORE grep/find or reading files when you need to understand or locate code. The skill at `~/.claude/skills/codebase-memory/` contains the full decision matrix and workflow.

## Quick Reference

| Question | Tool |
|----------|------|
| Who calls X? | `trace_path(direction="inbound")` |
| What does X call? | `trace_path(direction="outbound")` |
| Find by name | `search_graph(name_pattern="...")` |
| Dead code | `search_graph(max_degree=0)` |
| Impact of changes | `detect_changes()` |
| Architecture overview | `get_architecture(aspects=["all"])` |
| Read source | `get_code_snippet(qualified_name="...")` |

## Exploration Workflow

`list_projects` → `get_graph_schema` → `search_graph` → `get_code_snippet`

> If the repository hasn't been indexed yet, run: `codebase-memory-mcp cli index_repository '{"repo_path": "/path/to/repo"}'`
