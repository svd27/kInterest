val kotlinVersion : String by project
val spekVersion : String by project

plugins {
    kotlin("multiplatform")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                //implementation(kotlin("test-common"))
                //implementation(kotlin("test-info.kinterest.annotations-common"))
            }
        }
    }
    jvm().compilations["main"].defaultSourceSet {
        dependencies {
            implementation(kotlin("stdlib-jdk8"))
        }
    }
    js()
}
