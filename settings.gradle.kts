// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.

plugins {
    // Auto-provisions the Java 17 toolchain when it is not installed (laptops and CI runners alike).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "forge-fl-plugin"
