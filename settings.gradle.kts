rootProject.name = "Quelea"

plugins {
    id("io.micronaut.platform.catalog") version "5.0.2"
}

// Both onelitefeather.dev repos below share one account - read the same
// OneLiteFeatherRepositoryUsername/Password gradle properties (or CI env vars) for each,
// rather than each repo's own name-derived credential convention, so only one set of
// credentials needs to be configured locally/in CI.
fun org.gradle.api.artifacts.repositories.MavenArtifactRepository.oneLiteFeatherCredentials() {
    if (System.getenv("CI") != null) {
        credentials {
            username = System.getenv("ONELITEFEATHER_MAVEN_USERNAME")
            password = System.getenv("ONELITEFEATHER_MAVEN_PASSWORD")
        }
    } else {
        credentials {
            username = providers.gradleProperty("OneLiteFeatherRepositoryUsername").orNull
            password = providers.gradleProperty("OneLiteFeatherRepositoryPassword").orNull
        }
        authentication {
            create<BasicAuthentication>("basic")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "OneLiteFeatherRepository"
            url = uri("https://repo.onelitefeather.dev/onelitefeather")
            oneLiteFeatherCredentials()
        }
        // The aggregate "onelitefeather" repo above doesn't resolve artifacts published straight
        // to the underlying releases/snapshots repos - declare those explicitly too, same credentials.
        maven {
            name = "OneLiteFeatherReleasesRepository"
            url = uri("https://repo.onelitefeather.dev/onelitefeather-releases")
            oneLiteFeatherCredentials()
        }
        // Velocity API for the velocity module is published here.
        maven("https://repo.papermc.io/repository/maven-public/")
    }
    versionCatalogs {
        create("libs") {
            version("micronaut", "5.0.2")
            version("velocity", "4.3.1")
            version("shadow", "9.6.1")
            version("sentry", "8.62.0")
            version("jetbrains.annotations", "26.1.0")
            version("jackson", "2.22.4")
            version("jakarta-annotation", "3.0.0")
            version("openapi.generator", "7.26.0")
            version("logstash-logback-encoder", "9.0")
            version("opentelemetry-instrumentation-alpha", "2.20.1-alpha")
            version("janino", "3.1.12")

            library("jetbrains.annotations", "org.jetbrains", "annotations").versionRef("jetbrains.annotations")

            // Jackson
            library("jackson-bom", "com.fasterxml.jackson", "jackson-bom").versionRef("jackson")
            library("jackson-core", "com.fasterxml.jackson.core", "jackson-core").withoutVersion()
            library("jackson-annotations", "com.fasterxml.jackson.core", "jackson-annotations").withoutVersion()
            library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind").withoutVersion()
            library("jackson-datatype-jsr310", "com.fasterxml.jackson.datatype", "jackson-datatype-jsr310").withoutVersion()

            library("jackson-databind-nullable", "org.openapitools", "jackson-databind-nullable").version("0.2.13")
            library("jakarta-annotation-api", "jakarta.annotation", "jakarta.annotation-api").versionRef("jakarta-annotation")

            library("velocity-api", "com.velocitypowered", "velocity-api").versionRef("velocity")

            // Observability - JSON logging + OpenTelemetry (see backend/build.gradle.kts).
            // Version managed by the Micronaut platform BOM (opentelemetry-bom).
            library("opentelemetry-exporter-otlp", "io.opentelemetry", "opentelemetry-exporter-otlp").withoutVersion()
            library("logstash-logback-encoder", "net.logstash.logback", "logstash-logback-encoder").versionRef("logstash-logback-encoder")
            library("opentelemetry-logback-mdc", "io.opentelemetry.instrumentation", "opentelemetry-logback-mdc-1.0").versionRef("opentelemetry-instrumentation-alpha")
            library("janino", "org.codehaus.janino", "janino").versionRef("janino")

            library("sentry-bom", "io.sentry", "sentry-bom").versionRef("sentry")
            library("sentry", "io.sentry", "sentry").withoutVersion()
            library("sentry-logback", "io.sentry", "sentry-logback").withoutVersion()
            plugin("micronaut.application", "io.micronaut.application").versionRef("micronaut")
            plugin("micronaut.aot", "io.micronaut.aot").versionRef("micronaut")
            plugin("shadow", "com.gradleup.shadow").versionRef("shadow")
            plugin("openapi.generator", "org.openapi.generator").versionRef("openapi.generator")
        }
    }
}

include("backend")
include("client")
include("velocity")
