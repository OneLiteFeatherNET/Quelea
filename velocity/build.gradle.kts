plugins {
    id("xyz.jpenilla.run-velocity") version "3.1.0"
    `maven-publish`
    alias(libs.plugins.shadow)
}

dependencies {
    // Velocity provides the API (and its Gson, Adventure and slf4j) at runtime, so it is not shaded.
    compileOnly(libs.velocity.api)
    // Sentry is shaded and relocated (see shadowJar), so it cannot clash with other plugins on the proxy.
    implementation(platform(libs.sentry.bom))
    implementation(libs.sentry)

    testImplementation(libs.velocity.api)
    testImplementation(mn.junit.jupiter.api)
    testImplementation(mn.junit.jupiter.params)
    testRuntimeOnly(mn.junit.jupiter.engine)
    testRuntimeOnly(mn.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    processResources {
        // Velocity reads velocity-plugin.json; the version follows release-please through project.version.
        // The version must be a declared input, otherwise the build cache reuses a descriptor of another version.
        inputs.property("version", project.version.toString())
        filesMatching("velocity-plugin.json") {
            expand(mapOf("version" to project.version))
        }
    }
    shadowJar {
        archiveBaseName.set("quelea-velocity")
        archiveClassifier.set("")
        relocate("io.sentry", "net.onelitefeather.quelea.velocity.libs.sentry")
        mergeServiceFiles()
    }
    runVelocity {
        velocityVersion("4.3.1")
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        artifact(tasks.shadowJar)
        version = rootProject.version as String
        artifactId = "quelea-velocity"
        groupId = project.group as String
        pom {
            name = "Quelea Velocity"
            description = "A Velocity proxy plugin for the Quelea party system: commands, lobby-to-game party follow and presence reporting"
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
            url =
                if (version.toString().contains("SNAPSHOT") || version.toString().contains("BETA") || version.toString()
                        .contains("ALPHA")
                ) snapshotsRepoUrl else releasesRepoUrl
        }
    }
}
