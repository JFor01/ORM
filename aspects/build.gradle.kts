plugins {
    id("java-library")
    id("io.freefair.aspectj") version "8.13.1"
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
    api(project(":persistence"))
    api("org.aspectj:aspectjrt:1.9.23")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

}

tasks.test {
    useJUnitPlatform()
}