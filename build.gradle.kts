@file:Suppress("PropertyName")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
    id("io.freefair.github.package-registry-maven-publish").version("4.1.1")
    maven
}

val vertx_version = "4.0.0-milestone3"
val logback_version = "1.2.3"
val jsoup_version = "1.12.1"

group = "cn.mmooo"
version = vertx_version

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compile("io.vertx:vertx-core:$vertx_version")
    compile("io.vertx:vertx-web:$vertx_version")
    compile("io.vertx:vertx-web-client:$vertx_version")
    compile("io.vertx:vertx-circuit-breaker:$vertx_version")
    compile("io.vertx:vertx-lang-kotlin-coroutines:$vertx_version")
    compile("ch.qos.logback:logback-classic:$logback_version")

    testCompile("org.jsoup:jsoup:$jsoup_version")
}
repositories {
    mavenLocal()
    mavenCentral()
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}


github {
    username.set(project.properties["githubUsername"]?.toString())
    token.set(project.properties["githubToken"]?.toString())
}

publishing {
    publications {
        create<MavenPublication>("main") {
            from(components["java"])
        }
    }
}

