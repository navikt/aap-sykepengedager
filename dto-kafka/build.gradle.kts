plugins {
    `maven-publish`
    `java-library`
}

dependencies {
    implementation("com.github.navikt.aap-libs:kafka-interfaces:3.7.56")
    testImplementation(kotlin("test"))
}

group = "com.github.navikt.aap-sykepengedager"

tasks {
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "kafka-dto"
            version = project.findProperty("dto-kafka.version").toString()
            from(components["java"])
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/navikt/aap-sykepengedager")
                credentials {
                    username = "x-access-token"
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
