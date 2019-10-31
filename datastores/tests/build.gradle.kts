import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.lang.Integer.max

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

configurations.all {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
}

val coroutinesVersion: String by project
val striktVersion: String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":core:annotations"))
    implementation(project(":core:common"))
    testImplementation(kotlin("test-junit5"))
    kaptTest(project(":core:generator", "default"))
    implementation(project(":datastores:mongo", "default"))
    testImplementation(project(":datastores:hazelcast", "default"))
    testImplementation(project(":docker:docker-client", "default"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.jvmTarget = "1.8"
}


kapt {
    arguments { arg("targets", "jvm") }
}



tasks.test {
    if("windows" in System.getProperty("os.name").toLowerCase()) {
        environment("DOCKER_HOST", "tcp://127.0.0.1:2375")
    }
    systemProperty("io.netty.tryReflectionSetAccessible", false)

    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    maxParallelForks = max(Runtime.getRuntime().availableProcessors() / 2, 1)
    setForkEvery(1)

    testLogging {
        events("failed", "skipped", "passed")
        exceptionFormat = TestExceptionFormat.FULL
    }

    useJUnitPlatform() {

    }
}
