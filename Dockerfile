# symbanta
#
# SPDX-License-Identifier: MIT
# Copyright © Josep Bigorra <jjbigorra@gmail.com>

# Builder
FROM docker.io/library/maven:3.9-eclipse-temurin-25-alpine AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime
FROM docker.io/library/eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=build /app/target/*.jar application.jar

ENTRYPOINT ["java","-jar","/app/application.jar"]
