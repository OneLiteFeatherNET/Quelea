plugins {
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.micronaut.aot)
    `maven-publish`
}

dependencies {
    annotationProcessor(mn.micronaut.serde.processor)
    annotationProcessor(mn.micronaut.http.validation)
    annotationProcessor(mn.micronaut.openapi)
    compileOnly(mn.micronaut.openapi.annotations)
    implementation(mn.micronaut.http.validation)

    implementation(mn.micronaut.runtime)
    implementation("io.micronaut.validation:micronaut-validation")
    implementation(mn.micronaut.management)
    implementation(mn.micronaut.micrometer.core)
    implementation(mn.micronaut.micrometer.registry.prometheus)
    implementation(mn.micronaut.serde.jackson)
    // Reactor carries the SSE event stream (one multicast sink per replica, see event/EventBroadcaster).
    implementation(mn.micronaut.reactor)
    // Party state lives in Dragonfly (Redis protocol); the adapters arrive with the Redis tasks.
    implementation(mn.micronaut.redis.lettuce)
    implementation(mn.snakeyaml)
    implementation(mn.logback.core)
    implementation(mn.logback.classic)

    // Distributed tracing (OpenTelemetry). Spans/export are only active when
    // OTEL_TRACES_EXPORTER=otlp is set (prod/Docker) - see application.yml.
    implementation(mn.micronaut.tracing.opentelemetry.http)
    implementation(libs.opentelemetry.exporter.otlp)

    // Structured JSON logging for log aggregation + trace/log correlation.
    // logstash encoder renders JSON; the OTel MDC appender injects trace_id/span_id.
    implementation(libs.logstash.logback.encoder)
    implementation(libs.opentelemetry.logback.mdc)
    // Enables the <if>/<then>/<else> conditional in logback.xml.
    runtimeOnly(libs.janino)

    testImplementation(mn.junit.jupiter.api)
    testImplementation(mn.junit.jupiter.params)
    testRuntimeOnly(mn.junit.jupiter.engine)
    testImplementation(mn.micronaut.test.junit5)
    testImplementation(mn.micronaut.http.client)
    // Redis integration tests (tagged "integration") run against a Redis container (pulls in Testcontainers).
    testImplementation(mn.testcontainers.core)
    testImplementation(mn.testcontainers.redis)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("net.onelitefeather.quelea.backend.QueleaApplication")
}

graalvmNative.toolchainDetection.set(false)

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("net.onelitefeather.quelea.backend.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading.set(false)
        convertYamlToJava.set(false)
        precomputeOperations.set(true)
        cacheEnvironment.set(true)
        optimizeClassLoading.set(true)
        deduceEnvironment.set(true)
        optimizeNetty.set(true)
        // Keep logback.xml parsed at runtime so the env-driven JSON/plain switch
        // and ${...} substitutions work in the optimized (Docker/prod) jar.
        replaceLogbackXml.set(false)
    }
}

// The Docker image runs as an unprivileged user (uid 10001) so the Helm chart can enforce runAsNonRoot.
// The application files stay owned by root and are only readable by that user, so the process cannot
// rewrite its own code. The JVM only needs /tmp to be writable; the chart mounts an emptyDir there
// because it also runs the container with a read-only root file system.
tasks.named<io.micronaut.gradle.docker.MicronautDockerfile>("optimizedDockerfile") {
    instruction("RUN groupadd --system --gid 10001 quelea && useradd --system --uid 10001 --gid quelea --no-create-home quelea")
    instruction("USER 10001:10001")
}

// The layers keep the file modes of the Gradle cache, where a jar can be owner-only (0600). The image runs as
// uid 10001, which then cannot read it (e.g. janino, so logback has no console appender). Make the context
// world-readable before Docker copies it.
tasks.named("optimizedBuildLayers") {
    doLast {
        outputs.files.asFileTree.forEach { it.setReadable(true, false) }
    }
}

// Integration tests (@Tag("integration")) need Docker for Testcontainers. Run the rest without it
// via ./gradlew :backend:test -PexcludeTags=integration.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("excludeTags").orNull?.let { tags ->
        useJUnitPlatform { excludeTags(tags) }
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        artifact(project.tasks.optimizedJitJar)
        artifact(project.tasks.optimizedRunnerJitJar)
        artifact(project.tasks.runnerJar)
        artifact(project.tasks.jar)
        artifact(project.tasks.optimizedDistTar)
        artifact(project.tasks.optimizedDistZip)

        version = rootProject.version as String
        artifactId = "quelea-backend"
        groupId = rootProject.group as String
        pom {
            name = "Quelea"
            description = "Distributed party service for the OneLiteFeather network"
            url = "https://github.com/OneLiteFeatherNET/Quelea"
            licenses {
                license {
                    name = "AGPL-3.0"
                    url = "https://www.gnu.org/licenses/agpl-3.0.en.html"
                }
            }
            developers {
                developer {
                    id = "themeinerlp"
                    name = "Phillipp Glanz"
                    email = "p.glanz@madfix.me"
                }
            }
            scm {
                connection = "scm:git:git://github.com:OneLiteFeatherNET/Quelea.git"
                developerConnection = "scm:git:ssh://git@github.com:OneLiteFeatherNET/Quelea.git"
                url = "https://github.com/OneLiteFeatherNET/Quelea"
            }
        }
    }

    repositories {
        maven {
            authentication {
                credentials(PasswordCredentials::class) {
                    // Those credentials need to be set under "Settings -> Secrets -> Actions" in your repository
                    username = System.getenv("ONELITEFEATHER_MAVEN_USERNAME")
                    password = System.getenv("ONELITEFEATHER_MAVEN_PASSWORD")
                }
            }

            name = "OneLiteFeatherRepository"
            val releasesRepoUrl = uri("https://repo.onelitefeather.dev/onelitefeather-releases")
            val snapshotsRepoUrl = uri("https://repo.onelitefeather.dev/onelitefeather-snapshots")
            url = if (version.toString().contains("BETA") || version.toString().contains("ALPHA") || version.toString().contains("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }
    }
}
