plugins {
    id("java")
}

group = "sk.tuke.meta.persistance"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}


java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}