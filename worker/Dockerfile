FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY Worker.java .
EXPOSE 8084
CMD ["java", "Worker.java", "--id=worker-standalone", "--port=8084", "--version=v1", "--mode=NORMAL"]
