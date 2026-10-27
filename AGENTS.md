# AGENTS.md

This document outlines the guidelines, conventions, and operational constraints for AI agents working on the **Empty Server Stopper** mod codebase.

---

## 1. Language & Localization
- **All project artifacts MUST be strictly in English.**
  - Source code, comments, Javadoc, class/method names, and variable names.
  - Configuration keys and internal log/console messages.
  - Documentation files (such as `README.md`, `AGENTS.md`, and changelogs).
  - Git commit messages, branch names, and PR titles/descriptions.

---

## 2. Git Workflow & Branching
- **Never modify or commit directly to the primary/release branch.**
  - Always create a dedicated branch before making code or configuration changes (e.g., `26.x`, `feature/...`, `fix/...`, `compat/...`).
- **Commit Standards:**
  - Write clear, concise, and imperative commit messages in English (e.g., `Update Gradle config to support Minecraft 26.2 and 26.3`).
  - Keep commits focused and logically grouped.

---

## 3. Mod Architecture & Multi-Version Compatibility
- **Single Universal Build Goal:**
  - Aim for a single mod JAR capable of supporting multiple Minecraft versions simultaneously.
  - Core target range: Minecraft **`26.1.2` and above** (specifically `26.1.2`, `26.2`, `26.3`, and subsequent compatible point releases).
- **Future-Proof Codebase Design:**
  - Avoid relying on fragile, internal, or frequently changing Mojang mappings.
  - Rely on stable Fabric APIs (e.g., `ServerLifecycleEvents`, `ServerPlayConnectionEvents`, `CommandRegistrationCallback`) and Mojang Brigadier.
  - Keep mod behavior server-side only (`DedicatedServerModInitializer`, `environment: "*"`).
- **Dependency Management:**
  - Version properties and dependency constraints are managed centrally in `gradle.properties` (such as `minecraft_dependency`, `loader_dependency`, and `minecraft_version`).
  - Dynamic token expansion in `processResources` ensures `fabric.mod.json` remains synchronized with Gradle properties without manual duplication.

---

## 4. Development & Build Toolchain
- **Runtime & Compilation:**
  - Target Java: **Java 25** (`sourceCompatibility = JavaVersion.VERSION_25`, `targetCompatibility = JavaVersion.VERSION_25`, `release = 25`).
  - Toolchain: Gradle 9.5+ with `net.fabricmc.fabric-loom`.
- **IDE & Testing:**
  - The primary development and testing environment is **IntelliJ IDEA**.
  - All Gradle tasks and configurations must remain compatible with IntelliJ's Gradle integration and Fabric Loom run configurations (e.g., `runServer`, `cleanBuildRunServer`).
  - Version targets can be adjusted or overridden via project properties (e.g., `-Pminecraft_version=26.3` or `-Pminecraft_version=26.1.2`).
