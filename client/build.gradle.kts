plugins {
    `maven-publish`
    alias(libs.plugins.openapi.generator)
}

// The client is generated from the OpenAPI document the backend writes while compiling (META-INF/swagger/quelea.yml),
// so the client always matches the routes of the service it is built from.
val outDir = layout.buildDirectory.dir("generated/openapi")
val backendSpec = project(":backend").layout.buildDirectory.file("classes/java/main/META-INF/swagger/quelea.yml")

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.core)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.annotation.api)
}

// OpenAPI Generator configuration
openApiGenerate {
    generatorName.set("java")
    library.set("native")
    inputSpec.set(backendSpec.get().asFile.absolutePath)
    outputDir.set(outDir.get().asFile.absolutePath)
    apiPackage.set("net.onelitefeather.quelea.client.api")
    invokerPackage.set("net.onelitefeather.quelea.client.invoker")
    modelPackage.set("net.onelitefeather.quelea.client.model")
    additionalProperties.set(
        mapOf(
            "dateLibrary" to "java8",
            "useTags" to "true",
            "interfaceOnly" to "true",
            "useJakartaEe" to "true",
            "serializationLibrary" to "jackson",
            "artifactId" to "quelea-client",
            "groupId" to "net.onelitefeather",
            "artifactVersion" to rootProject.version.toString(),
            "buildTool" to "gradle",
            "generateBuilders" to "true",
            "useJava8Time" to "true",
            "hideGenerationTimestamp" to "true"
        )
    )
    globalProperties.set(
        mapOf(
            "apiTests" to "false",
            "modelTests" to "false"
        )
    )
}

sourceSets.named("main") {
    java.srcDir(outDir.map { it.dir("src/main/java") })
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.named("openApiGenerate") {
    // The generator reads the document that the backend compiler writes.
    dependsOn(":backend:compileJava")
}

tasks.named("compileJava") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.named("sourcesJar") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.named("javadoc") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks {
    javadoc {
        options {
            (this as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
        version = rootProject.version as String
        artifactId = "quelea-client"
        groupId = project.group as String
        pom {
            name = "Quelea Client API"
            description = "The generated client for the Quelea party service."
            url = "https://github.com/OneLiteFeatherNET/Quelea"
            licenses {
                license {
                    name = "The Apache License, Version 2.0"
                    url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
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
            url = if (version.toString().contains("SNAPSHOT") || version.toString().contains("BETA") || version.toString().contains("ALPHA")) snapshotsRepoUrl else releasesRepoUrl
        }
    }
}
