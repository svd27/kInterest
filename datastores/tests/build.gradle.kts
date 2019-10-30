import org.gradle.api.tasks.testing.logging.TestExceptionFormat

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
    systemProperty("io.netty.tryReflectionSetAccessible", false)
    testLogging {
        events("failed", "skipped", "passed")
        exceptionFormat = TestExceptionFormat.FULL
    }

    useJUnitPlatform() {

    }
}
