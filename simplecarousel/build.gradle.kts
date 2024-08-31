import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "${project.group}"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
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
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates("${project.group}", "simplecarousel", "${project.version}")

    pom {
        name.set("SimpleCarousel for Android")
        description.set("Simple components to make carousel UI for Android.")
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
                name.set("Jaeung Cheon")
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
