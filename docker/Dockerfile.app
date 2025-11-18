FROM eclipse-temurin:17-jre-jammy

# Working directory inside the container
WORKDIR /opt/traccar

# Copy main server jar
COPY target/tracker-server.jar /opt/traccar/tracker-server.jar

# Copy all dependency jars (adjust path if different)
COPY target/lib /opt/traccar/lib

# Copy schema files for database migrations
COPY schema /opt/traccar/schema

# Copy resources (includes openapi.yaml for Swagger)
COPY build/resources/main /opt/traccar/resources

# Create empty web directory (API-only, no frontend served)
RUN mkdir -p /opt/traccar/web

# Note: Web UI removed - API-only backend

# Use your debug config (or switch to setup/traccar.xml if you prefer)
COPY debug.xml /opt/traccar/conf/traccar.xml
# COPY setup/traccar.xml /opt/traccar/conf/traccar.xml

# Expose web UI/API and device ports
EXPOSE 8082
EXPOSE 5000-5500

# Use env variables (from docker-compose) for DB and other config
ENV CONFIG_USE_ENVIRONMENT_VARIABLES=true

# Build classpath: all jars in lib plus the main jar
# Pass config file path as the last argument to Main class
ENTRYPOINT ["sh", "-c", "java -cp '/opt/traccar/resources:/opt/traccar/tracker-server.jar:/opt/traccar/lib/*' org.traccar.Main /opt/traccar/conf/traccar.xml"]