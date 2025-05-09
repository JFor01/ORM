plugins {
    id("application")
    id("io.freefair.aspectj.post-compile-weaving") version "8.13.1"
}

group = "sk.tuke.meta"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":persistence"))
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    annotationProcessor(project(":processor"))
    aspect(project(":aspects"))
}


application {
    mainClass = "sk.tuke.meta.example.Main"
}

tasks.test {
    useJUnitPlatform()
}
