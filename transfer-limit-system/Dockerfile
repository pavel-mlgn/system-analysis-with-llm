FROM maven:3.9.9-eclipse-temurin-21 AS poms
WORKDIR /src
COPY . .
RUN mkdir /poms && find . -name pom.xml -not -path '*/target/*' -exec cp --parents {} /poms \;

FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /build
COPY --from=poms /poms/ ./
RUN mvn -B -q -pl ${MODULE} -am dependency:go-offline
COPY transfer-common/src transfer-common/src
COPY ${MODULE}/src ${MODULE}/src
RUN mvn -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --uid 1001 --home-dir /app --no-create-home app
WORKDIR /app
COPY --from=build /build/${MODULE}/target/${MODULE}-*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
