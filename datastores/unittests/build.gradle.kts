plugins {
    kotlin("jvm")
    kotlin("kapt")
}

configurations.all {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
}

val spekVersion: String by project
val junitVersion: String by project
val coroutinesVersion: String by project
val striktVersion: String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":core:annotations"))
    implementation(project(":core:common"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:2.2.0")
    kaptTest(project(":core:generator", "default"))
    implementation(project(":datastores:mongo", "default"))
    testImplementation(project(":datastores:hazelcast", "default"))
    testImplementation(project(":docker:docker-client", "default"))
    testImplementation(platform("io.strikt:strikt-bom:$striktVersion"))
    testImplementation("io.strikt:strikt-java-time")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.testcontainers:testcontainers:1.12.2")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.jvmTarget = "1.8"
}


kapt {
    arguments { arg("targets", "jvm") }
}



tasks.test {
    environment("DOCKER_HOST", "tcp://localhost:2375")
    useJUnitPlatform() {
        includeEngines("spek2", "junit-jupiter")
    }
}
