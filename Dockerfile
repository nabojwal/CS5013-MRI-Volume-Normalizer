# Project PAI CS5013 — headless evaluation/demo image (not the Swing desktop GUI).
# Maven builds the *existing* Java source. The runtime contains only classes,
# runtime dependencies, a synthetic nonpatient test volume, and verification code.
FROM maven:3.9.13-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
# Resolve dependencies separately for repeatable caching.
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY src/ ./src/
RUN mvn -B -ntp -DskipTests package dependency:copy-dependencies -DincludeScope=runtime

FROM eclipse-temurin:21-jre-noble AS runtime
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="Project PAI headless MRI converter" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.description="Bounded nonclinical FBO/EBO conversion and synthetic verification"
# Python is used only by the independent (stdlib-only) demo verifier.
RUN apt-get update && apt-get install -y --no-install-recommends python3 coreutils \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/pai
COPY --from=build /build/target/classes/ /opt/pai/classes/
COPY --from=build /build/target/dependency/ /opt/pai/lib/
COPY docker/entrypoint.sh /opt/pai/entrypoint.sh
COPY docker/verify_demo.py /opt/pai/verify_demo.py
COPY docker/make_fixture.py /opt/pai/make_fixture.py
# Normalize CRLF, since the source may be checked out on Windows.
RUN sed -i 's/\r$//' /opt/pai/entrypoint.sh && chmod 0755 /opt/pai/entrypoint.sh \
    && mkdir -p /opt/pai/fixture /work \
    && python3 -B /opt/pai/make_fixture.py /opt/pai/fixture/demo.nii
ENV PYTHONDONTWRITEBYTECODE=1
# Docker-created Linux local volumes are required for the ext4-only M8 policy.
# Host bind mounts from C:\ may be shared filesystems and are NOT qualified.
VOLUME ["/work"]
ENTRYPOINT ["/opt/pai/entrypoint.sh"]
CMD ["demo"]
