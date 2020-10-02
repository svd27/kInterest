import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

configurations.all {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
}

val coroutinesVersion: String by project
val striktVersion: String by project
val kotlinSerializationVersion : String by project
val koTestVersion : String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":core:annotations"))
    implementation(project(":core:common"))
    testImplementation(kotlin("test-junit5"))
    kaptTest(project(":core:generator", "default"))
    implementation(project(":datastores:mongo", "default"))
    testImplementation(project(":datastores:hazelcast", "default"))
    testImplementation(project(":datastores:hazelcast:jet", "default"))
    testImplementation(project(":docker:docker-client", "default"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$koTestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$koTestVersion")
    testImplementation("io.kotest:kotest-property:$koTestVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.jvmTarget = "1.8"
}


kapt {
    arguments { arg("targets", "jvm") }
}

tasks.build {
    dependsOn(":datastores:hazelcast:jet:fatJar")
}

tasks.test {
    dependsOn(":docker:docker-client:copyFat")

    if("windows" in System.getProperty("os.name").toLowerCase()) {
        environment("DOCKER_HOST", "tcp://127.0.0.1:2375")
    }
    systemProperty("io.netty.tryReflectionSetAccessible", false)

    //systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    //maxParallelForks = max(Runtime.getRuntime().availableProcessors() / 2, 1)
    //setForkEvery(1)

    testLogging {
        events("failed", "skipped", "passed")
        exceptionFormat = TestExceptionFormat.FULL
    }

    useJUnitPlatform() {

    }
}
