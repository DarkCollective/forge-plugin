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

dependencies {
    testImplementation("junit:junit:4.13.2")
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

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("999.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    test {
        // Run the optional corpus check: ./gradlew test -Dfl.corpus=/path/to/dir-of-.fth-files
        System.getProperty("fl.corpus")?.let { systemProperty("fl.corpus", it) }
    }
}
