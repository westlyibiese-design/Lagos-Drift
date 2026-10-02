plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val gdxVersion = "1.12.1"
val natives by configurations.creating

android {
    namespace = "com.westly.lagosdrift"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.westly.lagosdrift"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // libGDX native libraries are copied into app/libs/<abi>/ during the build.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")

    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
}

// Unpacks the libGDX native .so files into app/libs/<abi>/ so they end up in the APK.
tasks.register("copyAndroidNatives") {
    doFirst {
        val abiByJarSuffix = mapOf(
            "natives-armeabi-v7a.jar" to "armeabi-v7a",
            "natives-arm64-v8a.jar" to "arm64-v8a",
            "natives-x86.jar" to "x86",
            "natives-x86_64.jar" to "x86_64"
        )
        natives.files.forEach { jar ->
            val abi = abiByJarSuffix.entries.firstOrNull { jar.name.endsWith(it.key) }?.value
            if (abi != null) {
                val outDir = file("libs/$abi")
                outDir.mkdirs()
                project.copy {
                    from(project.zipTree(jar))
                    into(outDir)
                    include("*.so")
                }
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn("copyAndroidNatives")
}
