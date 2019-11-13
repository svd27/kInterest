import org.gradle.api.tasks.testing.logging.TestExceptionFormat

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
val hazelcastVersion : String by project

val kotlinTestVersion : String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":core:common"))
    implementation("org.dom4j:dom4j:2.1.1")
    implementation("jaxen:jaxen:1.1.6")
    implementation("javax.xml.stream:stax-api:1.0-2")
    implementation("net.java.dev.msv:xsdlib:2013.6.1")
    implementation("javax.xml.bind:jaxb-api:2.2.12")
    implementation("pull-parser:pull-parser:2")
    implementation("xpp3:xpp3:1.1.4c")


    implementation("io.github.microutils:kotlin-logging:1.7.6")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.github.docker-java:docker-java:3.2.0-rc1")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    testImplementation(platform("io.strikt:strikt-bom:$striktVersion"))
    testImplementation("io.strikt:strikt-java-time")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.mongodb:mongodb-driver-reactivestreams:1.12.0")
    testImplementation("com.hazelcast:hazelcast-client:$hazelcastVersion")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:$kotlinTestVersion")
}

tasks {
    register("copyFat", Copy::class) {
        dependsOn(":datastores:hazelcast:jet:fatJar")
        val f = project(":datastores:hazelcast:jet").file("build/libs/jet-fat.jar")
        from(f.absolutePath)
        into("src/main/resources")
    }

    "build" {
        dependsOn("copyFat")
    }

    assemble {
        dependsOn("copyFat")
    }
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
    useJUnitPlatform() {
        includeEngines ("spek2", "junit-jupiter")
    }

    testLogging {
        events("failed", "skipped", "passed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
