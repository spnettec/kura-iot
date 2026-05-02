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
    ├── com.yofc.iot.repack.vertx.db # Repackaged Vert.x SQL clients + JDBC drivers
    └── yofc-repo                    # P2 update site for Jersey JAX-RS bundles
```

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
The `repack` module bundles Vert.x SQL clients with JDBC drivers (PostgreSQL, MySQL, MSSQL, Oracle) into a single OSGi bundle to resolve OSGi dependency issues.

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
