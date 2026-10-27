# Development Guide

This guide details the development workflows, multi-version architecture, and testing tips for **Empty Server Stopper**.

---

## 1. Prerequisites & Toolchain

- **Java Development Kit (JDK):** **Java 25** is strictly required.
  - Recommended: Eclipse Temurin 25 or the JetBrains Runtime (JBR 25) bundled with IntelliJ IDEA (located under `<IntelliJ_Installation_Dir>\jbr`).
- **Build Tool:** Gradle 9.5+ (use the included `./gradlew` wrapper).
- **IDE:** **IntelliJ IDEA** (2026.1+ recommended).

### Windows Local Script (`gradle.ps1`)
On Windows machines where `JAVA_HOME` is not set globally, a local helper script [`gradle.ps1`](gradle.ps1) is available. It automatically detects IntelliJ IDEA's bundled JBR 25 or Temurin 25:
```powershell
.\gradle.ps1 cleanBuildRunServer -Pminecraft_version=26.2
```

---

## 2. Multi-Version Architecture & Universal Build

The project aims for a **single universal mod JAR** that works out of the box across multiple Minecraft releases:
- **Target Range:** Minecraft **`26.1.2` and above** (`26.1.2`, `26.2`, `26.3+`).
- **Fabric Dependencies:** In `fabric.mod.json`, the dependencies are dynamically injected from `gradle.properties`:
  - `"minecraft": ">=26.1.2"`
  - `"fabricloader": ">=0.19.0"`
  - `"fabric-api": "*"`

### Mod Versioning & Artifact Nomenclature
The mod adheres to standard Fabric versioning:
$$\text{version} = \langle\text{mod\_version}\rangle+\langle\text{minecraft\_version}\rangle$$

- Default build (latest supported target): **`1.2.2+26.3`**
- Resulting JAR: `build/libs/emptyserverstopper-1.2.2+26.3.jar`
- When targeting an older version for testing (e.g. `-Pminecraft_version=26.2`), the artifact dynamically reflects it: `emptyserverstopper-1.2.2+26.2.jar`.

---

## 3. Testing Across Different Minecraft Versions

The build configuration contains an automated lookup table for Fabric API versions:

| Minecraft Target | Mapped Fabric API |
|:-----------------|:------------------|
| `26.3` (default) | `0.165.0+26.3`    |
| `26.2`           | `0.160.0+26.2`    |
| `26.1.2`         | `0.154.2+26.1.2`  |

### Running from Terminal
```powershell
# Default target (Minecraft 26.3)
.\gradlew cleanBuildRunServer

# Specific version targets
.\gradlew cleanBuildRunServer -Pminecraft_version=26.2
.\gradlew cleanBuildRunServer -Pminecraft_version=26.1.2

# Explicit Fabric API override (if testing unmapped versions)
.\gradlew cleanBuildRunServer -Pminecraft_version=26.2 -Pfabric_api_version=0.160.0+26.2
```

### Running from IntelliJ IDEA
1. Open **Run/Debug Configurations**.
2. Select your `cleanBuildRunServer` configuration.
3. In **Arguments** (or **Run**), append `-Pminecraft_version=<version>` (e.g. `cleanBuildRunServer -Pminecraft_version=26.2`).
4. Apply and Run.

---

## 4. Avoiding World Downgrade Corruption

> [!WARNING]
> Minecraft **does not support world downgrades**. If a development world is generated on Minecraft 26.3 and subsequently opened with Minecraft 26.2, chunk palettes fail to deserialize, spawner entities turn into air, and chunks become empty.

To prevent this issue during iterative testing:
- **`cleanBuildRunServer`** automatically depends on **`cleanWorld`**, resetting `run/world` before starting the server.
- To wipe only the world without cleaning build artifacts:
  ```powershell
  .\gradlew cleanWorld
  ```

---

## 5. Development Conventions & Future-Proofing

- **Server-Side Exclusivity:** Empty Server Stopper is strictly server-side (`DedicatedServerModInitializer`, `environment: "*"`).
- **APIs to Rely On:**
  - Fabric Server Lifecycle: `ServerLifecycleEvents.SERVER_STARTING`, `SERVER_STARTED`, `SERVER_STOPPING`
  - Fabric Networking: `ServerPlayConnectionEvents.JOIN`, `DISCONNECT`
  - Mojang Brigadier: `CommandRegistrationCallback.EVENT`
- **APIs to Avoid:** Do not import or depend on internal volatile Mojang rendering, client, or mapping-heavy gameplay systems.
- **Language:** All source code, commit messages, comments, Javadoc, and documentation MUST strictly be in English.
