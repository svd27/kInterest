/*
 * This file was generated by the Gradle 'init' task.
 *
 * This is a general purpose Gradle build.
 * Learn how to create Gradle builds at https://guides.gradle.org/creating-new-gradle-builds
 */
repositories {
    mavenCentral()
    jcenter()
}

version = "0.1.0"

plugins {
    kotlin("multiplatform") apply false
}

allprojects {
    group = "info.kinterest"
    repositories {
        mavenCentral()
        jcenter()
    }

}
