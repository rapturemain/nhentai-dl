plugins {
    kotlin("jvm") version "2.0.0"
    application
}

group = "org.lifeutils"
version = "1.0"

repositories {
    mavenCentral()
}

val ktorVersion: String by project

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.jsoup:jsoup:1.18.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-simple:2.0.16")

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.lifeutils.nhentaidl.cli.MainKt"
    }

    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "org.lifeutils.nhentaidl.cli.MainKt"
}
