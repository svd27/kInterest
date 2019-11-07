plugins {
    kotlin("jvm")
    kotlin("kapt")
}

configurations.all {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
}

val spekVersion : String by project

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.3")
    implementation(project(":core:annotations"))
    implementation(project(":core:common"))
    implementation(project(":core:jvm-backend", "default"))
    implementation("com.squareup:kotlinpoet:1.4.3")
    implementation("com.squareup:kotlinpoet-metadata:1.4.3")
    implementation("com.google.auto.service:auto-service:1.0-rc6")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    kapt("com.google.auto.service:auto-service:1.0-rc6")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    testImplementation(platform("io.strikt:strikt-bom:0.22.2"))

    // Versions can be omitted as they are supplied by the BOM
    testImplementation("io.strikt:strikt-java-time")
}

tasks.test {
    useJUnitPlatform() {
        includeEngines ("spek2")
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
