@file:Suppress("PropertyName")

import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm") version "1.3.60"
    maven
}


group = "cn.mmooo"
version = "4.0.0-milestone4"

val vertx_version = version
val logback_version = "1.2.3"
val jsoup_version = "1.12.1"

dependencies {
    api(platform("com.fasterxml.jackson:jackson-bom:2.10.1"))
    implementation(kotlin("stdlib-jdk8"))
    api("io.vertx:vertx-core:$vertx_version")
    api("io.vertx:vertx-web:$vertx_version")
    api("io.vertx:vertx-web-client:$vertx_version")
    api("io.vertx:vertx-circuit-breaker:$vertx_version")
    api("io.vertx:vertx-lang-kotlin-coroutines:$vertx_version")
    api("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.jsoup:jsoup:$jsoup_version")
}

repositories {
    mavenLocal()
    jcenter()
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
