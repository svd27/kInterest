@file:Suppress("UNUSED_VARIABLE")

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val coroutinesVersion: String by project
val kodeinVersion: String by project
val kotlinSerializationVersion : String by project
val koTestVersion : String by project

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("io.github.microutils:kotlin-logging-common:1.7.6")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
    jvm().compilations["main"].defaultSourceSet {
        dependencies {
            implementation(kotlin("stdlib-jdk8"))
            implementation(kotlin("reflect"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$coroutinesVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
            implementation("org.kodein.di:kodein-di:$kodeinVersion")
            implementation("org.kodein.di:kodein-di-conf-jvm:$kodeinVersion")
            implementation("io.github.microutils:kotlin-logging:1.7.6")
            implementation("ch.qos.logback:logback-classic:1.2.3")
            implementation("io.github.config4k:config4k:0.4.1")
            implementation("com.beust:klaxon:5.0.1")
        }
    }
    jvm().compilations["test"].defaultSourceSet {
        dependencies {
            implementation(kotlin("test-junit5"))
            implementation("io.kotest:kotest-runner-junit5:$koTestVersion")
            implementation("io.kotest:kotest-assertions-core:$koTestVersion")
            implementation("io.kotest:kotest-property:$koTestVersion")
        }
    }

    js {
        browser()
    }
    js().compilations["main"].defaultSourceSet {
        dependencies {
            implementation(kotlin("stdlib-js"))
            implementation("io.github.microutils:kotlin-logging-js:1.7.6")
        }
    }

    js().compilations["test"].defaultSourceSet {
        dependencies {
            implementation(kotlin("test-js"))
        }
    }
}
