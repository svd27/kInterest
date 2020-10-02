plugins {
    kotlin("jvm")
    java
}

configurations.all {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
}

val spekVersion : String by project
val junitVersion : String by project
val hazelcastVersion : String by project
val hazelcastJetVersion : String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(project(":core:annotations"))
    implementation(project(":core:common"))
    implementation("com.hazelcast:hazelcast:$hazelcastVersion")
    implementation("com.hazelcast.jet:hazelcast-jet:$hazelcastJetVersion")
    implementation(project(":datastores", "default"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    testImplementation("com.hazelcast:hazelcast:$hazelcastVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}



val fatJar = task("fatJar", type = Jar::class) {
    @Suppress("DEPRECATION")
    baseName = "${project.name}-fat"
    manifest {
        attributes["Implementation-Title"] = "Jet Server code"
        attributes["Implementation-Version"] = archiveVersion
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.jvmTarget = "1.8"
}
