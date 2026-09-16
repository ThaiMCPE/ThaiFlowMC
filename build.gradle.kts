plugins {
    java
}

allprojects {
    group = "dev.thaiflowmc"
    version = property("thaiflowmcVersion") as String

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    // Minecraft 1.20.5+ requires Java 21, so all ThaiFlowMC modules target that
    // language level. We compile with `--release 21` rather than a Gradle Java
    // toolchain so the build doesn't need to provision a separate JDK.
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        // Silences JDK warnings from running GraalPy on a stock (non-GraalVM) JDK.
        jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
    }

    dependencies {
        add("implementation", "org.slf4j:slf4j-api:2.0.17")

        add("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.4")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }
}
