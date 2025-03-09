# Use Amazon Corretto 21 (Java 21) as the base image
FROM amazoncorretto:21

# Set working directory (if not already set by base image)
WORKDIR /app

# Copy the service's jar file into the container
COPY target/scrapingservice.jar /app/scrapingservice.jar

# (Optional) If any additional setup is needed (e.g., installing packages), ensure using Amazon Linux commands.
# For example, if previously using 'apt-get' on Debian, use 'yum' for Amazon Linux. 
# (No additional packages needed in this case.)

# Expose the service port (if applicable)
EXPOSE 8080

# Run the application using Java 21
ENTRYPOINT ["java", "-jar", "/app/scrapingservice.jar"]
