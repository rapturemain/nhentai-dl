plugins {
    kotlin("jvm") version "2.0.0"
}

group = "org.lifeutils"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.jsoup:jsoup:1.18.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}