# Orders API Demo
This is a demo for showcasing how to use and the advantages of the Loom project using Java 22.

## TODO:
Think if there is an interesting scenario to showcase the endpoints in the controller

## How to run database
1. `docker-compose down -v`
2. `docker-compose up`

## How to run the application
1. `mvn clean compile -DcompilerArgs="--enable-preview"`
2. `mvn spring-boot:run -Dspring-boot.run.jvmArguments="--enable-preview"

## How to run runner
`mvn spring-boot:run -Dspring-boot.run.jvmArguments="--enable-preview" \
-Dspring-boot.run.arguments="--demo.threadType=virtual --demo.numThreads=100000 --demo.showThreads=false --demo.logInterval=10000"
`

