plugins {
    kotlin("jvm")
    kotlin("kapt")
}

configurations.all {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
}

val spekVersion : String by project
val junitVersion : String by project
val coroutinesVersion : String by project
val striktVersion : String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.github.microutils:kotlin-logging:1.7.6")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.github.docker-java:docker-java:3.2.0-rc1")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:2.2.0")
    kaptTest(project(":core:generator", "default"))
    testImplementation(platform("io.strikt:strikt-bom:$striktVersion"))
    testImplementation("io.strikt:strikt-java-time")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.jvmTarget = "1.8"
}


kapt {
    arguments { arg("targets", "jvm") }
}



tasks.test {
    useJUnitPlatform() {
        includeEngines ("spek2", "junit-jupiter")
    }
}
