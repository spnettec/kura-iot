# Maven workspace integration

The project inherits `org.eclipse.kura:kura-build-parent:6.0.0-SNAPSHOT` from Maven.
Build/install Kura build support and its API first, then import this POM in IDEA.
Java 21, Maven Bundle Plugin 6.0.0 and the existing annotation-generated DS/metatype
remain in use. Generated manifests and DS exports now stay under `target/`.

PLC4X remains an independent project. Install the required `1.0.0.B-SNAPSHOT`
artifacts from its source checkout into the same Maven repository before building
YOFC. Kura's workspace reactor does not build or modify PLC4X.

`mvn test` runs JUnit 5 tests without an OSGi or Docker prerequisite. The socket
fixture opens a loopback server; OPC UA uses the simulated PLC driver and stores
its database under `target/test-tmp`. Existing hardware/external database tests
remain explicitly disabled. No tests connect to production endpoints.

Validated on 2026-10-09 with Maven 3.10 / Java 21 and an isolated Maven repository:
43 tests passed, 6 existing hardware/external tests skipped. The EventBusBridge
WebSocketClient lifetime regression and test-fixture fixes are separate commits.

All eight bundle manifests retain their import/export ranges, DS declarations,
private library layout and resources. Explicit ranges preserve the previous
optional OPC UA imports and the API's package substitution range. InfluxDB keeps
its private Gson 2.12.1 / error-prone annotations 2.36.0 despite the shared BOM.

The 35 rebuilt PLC4X embedded JARs differ only in their own MANIFEST.MF (including
bnd's package substitution imports). Their classes, services, licenses and other
resources are byte-identical to the frozen baseline. Embedded JAR manifests do
not define OSGi wiring; the outer OPC UA bundle's manifest is unchanged. These
artifacts are consumed as normal private JARs, not installed as PLC4X bundles.
