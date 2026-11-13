// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "com.forge"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    // Gradle itself runs on any JDK 17+; code is compiled and tested on a Java 17 toolchain
    // (the Java level of IntelliJ Platform 2024.1, the oldest supported build).
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // Compile and test against the oldest supported platform (since-build 241).
        intellijIdeaCommunity("2024.1.1")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
            // Latest stable release at the time of writing is IntelliJ IDEA 2026.3 (branch 263).
            // Raise this after verifying the next release with ./gradlew verifyPlugin.
            untilBuild = "263.*"
        }
    }

    // Searchable options are only needed for settings pages; this plugin has none of its own.
    buildSearchableOptions = false

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.1.1")
            create(IntelliJPlatformType.IntellijIdea, "2026.3")
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    // Ship the licence and notice inside the plugin jar (META-INF/LICENSE.txt, META-INF/NOTICE).
    processResources {
        from(layout.projectDirectory) {
            include("LICENSE.txt", "NOTICE")
            into("META-INF")
        }
    }

    test {
        // Run the optional corpus check: ./gradlew test -Dfl.corpus=/path/to/dir-of-.fth-files
        System.getProperty("fl.corpus")?.let { systemProperty("fl.corpus", it) }
        // FLStyleGuideTest checks every example of the style guide against the formatter
        inputs.file("docs/FL-STYLE.md").withPropertyName("styleGuide")
    }
}
