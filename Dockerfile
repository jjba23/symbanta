# symbanta

# Copyright © Josep Bigorra <jjbigorra@gmail.com>
#
# symbanta is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# symbanta is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with symbanta.  If not, see <https://www.gnu.org/licenses/>.
#

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
