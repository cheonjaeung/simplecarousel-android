plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "${project.group}.pager"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    implementation(libs.androidx.core)
    api(libs.androidx.recyclerview)
    api(libs.androidx.viewpager2)
    api(project(":simplecarousel"))
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("${project.group}", "simplecarousel-pager", "${project.version}")

    pom {
        name.set("SimpleCarousel Pager for Android")
        description.set("Simple components to make carousel pager UI for Android.")
        url.set("https://github.com/cheonjaeung/simplecarousel-android")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("cheonjaeung")
                name.set("Cheon Jaeung")
                email.set("cheonjaewoong@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/cheonjaeung/simplecarousel-android")
            connection.set("scm:git:git://github.com/cheonjaeung/simplecarousel-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/cheonjaeung/simplecarousel-android.git")
        }
    }
}
