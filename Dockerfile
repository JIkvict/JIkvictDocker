
FROM eclipse-temurin:25-jdk

WORKDIR /app

RUN apt-get update && \
    apt-get install -y unzip curl git && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV GRADLE_VERSION=9.1.0
ENV GRADLE_HOME=/opt/gradle
ENV PATH=${GRADLE_HOME}/bin:${PATH}
ENV GRADLE_USER_HOME=/gradle-cache

RUN curl -L https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -o gradle.zip && \
    mkdir -p ${GRADLE_HOME} && \
    unzip -q gradle.zip -d /opt && \
    mv /opt/gradle-${GRADLE_VERSION}/* ${GRADLE_HOME} && \
    rm -rf /opt/gradle-${GRADLE_VERSION} && \
    rm gradle.zip && \
    mkdir -p ${GRADLE_USER_HOME}

RUN mkdir -p ${GRADLE_USER_HOME}/wrapper/dists/gradle-${GRADLE_VERSION}-bin && \
    curl -L https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
    -o ${GRADLE_USER_HOME}/wrapper/dists/gradle-${GRADLE_VERSION}-bin/gradle-${GRADLE_VERSION}-bin.zip && \
    cd ${GRADLE_USER_HOME}/wrapper/dists/gradle-${GRADLE_VERSION}-bin && \
    unzip -q gradle-${GRADLE_VERSION}-bin.zip && \
    mv gradle-${GRADLE_VERSION} $(echo -n "gradle-${GRADLE_VERSION}-bin.zip" | md5sum | cut -d' ' -f1) && \
    rm gradle-${GRADLE_VERSION}-bin.zip

COPY build/libs/JIkvictDocker-1.0-SNAPSHOT.jar /app/solution-runner.jar

RUN mkdir -p /app/input /app/preloaded-deps

COPY preloaded-deps-build.gradle /app/preloaded-deps/build.gradle

WORKDIR /app/preloaded-deps
RUN gradle downloadDependencies --no-daemon

WORKDIR /app

ENTRYPOINT ["java", "-jar", "/app/solution-runner.jar"]

CMD ["/app/input/solution.zip", "300"]