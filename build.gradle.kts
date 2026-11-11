plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.0"
}

group = "com.forge"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellij {
    version.set("2024.1.1")
    type.set("IC")
    plugins.set(listOf())
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    named("initializeIntelliJPlugin") {
        enabled = false
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("262.*")
    }

    buildSearchableOptions {
        enabled = false
    }
}
