plugins {
    kotlin("jvm")
    kotlin("kapt")
}

configurations.all {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
}

val spekVersion : String by project
val junitVersion : String by project
val hazelcastVersion : String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":core:annotations"))
    implementation(project(":core:common"))
    implementation("org.mongodb:mongodb-driver-reactivestreams:1.12.0")
    implementation("com.hazelcast:hazelcast-client:$hazelcastVersion")
    implementation(project(":datastores", "default"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:2.2.0")
    testImplementation("com.hazelcast:hazelcast:$hazelcastVersion")
    kaptTest(project(":core:generator", "default"))
}

kapt {
    arguments { arg("targets", "jvm") }
}



tasks.test {
    useJUnitPlatform() {
        includeEngines ("spek2")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.jvmTarget = "1.8"
}
