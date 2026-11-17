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
    // Remote Robot (UI tests driving a running IDE): the client library and the robot server plugin
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    intellijPlatform {
        defaultRepositories()
    }
}

// UI tests (src/uiTest): drive the IDE started by runIdeForUiTests through its robot server. Never part of `test`.
val uiTest: SourceSet by sourceSets.creating
val uiTestImplementation: Configuration by configurations.getting

java {
    // Gradle itself runs on any JDK 17+; code is compiled and tested on a Java 17 toolchain
    // (the Java level of IntelliJ Platform 2024.1, the oldest supported build).
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    uiTestImplementation("junit:junit:4.13.2")
    uiTestImplementation("com.intellij.remoterobot:remote-robot:0.11.23")

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

// The sandbox IDE for UI tests: ./gradlew runIdeForUiTests (opens build/ui-project), then ./gradlew uiTest.
val uiProject = layout.buildDirectory.dir("ui-project")
intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Didea.trust.all.projects=true",
                        "-Dide.show.tips.on.startup.default.value=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                        "-Didea.suppress.statistics.report=true",
                        "-Dide.mac.file.chooser.native=false",
                        "-Dapple.laf.useScreenMenuBar=false",
                    )
                }
                args(uiProject.get().asFile.absolutePath)
                // the project the UI tests work in; uiTest writes demo.fth's content itself
                doFirst {
                    val demo = uiProject.get().file("demo.fth").asFile
                    if (!demo.exists()) {
                        demo.parentFile.mkdirs()
                        demo.writeText("IN: demo\n")
                    }
                }
            }
            plugins {
                robotServerPlugin("0.11.23")
            }
        }
    }
}

tasks.register<Test>("uiTest") {
    description = "Drives the IDE of runIdeForUiTests (start it first) through Remote Robot"
    testClassesDirs = uiTest.output.classesDirs
    classpath = uiTest.runtimeClasspath
    useJUnit()
    outputs.upToDateWhen { false }
    // the Remote Robot client's Gson reads Throwable fields
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    systemProperty("ui.screenshots", layout.buildDirectory.dir("ui-screenshots").get().asFile.absolutePath)
    systemProperty("ui.project", uiProject.get().asFile.absolutePath)
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
        // Format a directory in place / check it without writing: -Dfl.format=DIR, -Dfl.formatCheck=DIR
        System.getProperty("fl.format")?.let { systemProperty("fl.format", it) }
        System.getProperty("fl.formatCheck")?.let { systemProperty("fl.formatCheck", it) }
        // Run the bundled FL runner against a built checkout: ./gradlew test -Dfl.forge=/path/to/forge
        System.getProperty("fl.forge")?.let { systemProperty("fl.forge", it) }
        // FLStyleGuideTest checks every example of the style guide against the formatter
        inputs.file("docs/FL-STYLE.md").withPropertyName("styleGuide")
    }
}
