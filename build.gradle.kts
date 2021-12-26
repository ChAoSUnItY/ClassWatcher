plugins {
    java
    application
}

group = "org.casc.lang"
version = "1.0.0"

repositories {
    mavenCentral()
}

application {
    mainClass.set("org.casc.lang.ClassWatcher")
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "org.casc.lang.ClassWatcher"
    }
}
