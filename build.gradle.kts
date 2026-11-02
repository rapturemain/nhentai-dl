plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "org.lifeutils"
version = "1.1.1"

repositories {
    mavenCentral()
}

val jsoupVersion = "1.23.2"
val ktorVersion = "3.6.1"
val slf4jVersion = "2.0.20"
val junitVersion = "5.13.4"

dependencies {
    implementation("org.jsoup:jsoup:$jsoupVersion")

    implementation("tools.jackson.core:jackson-core:3.2.4")
    implementation("tools.jackson.module:jackson-module-kotlin:3.2.4")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.assertj:assertj-core:3.27.7")
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
