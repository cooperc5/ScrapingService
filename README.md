# ScrapingService

ScrapingService is a microservice that collects data from websites and processes it for downstream use. It is built with Java and packaged as a Spring Boot application.

## Requirements

- **Java 21** (JDK 21, Amazon Corretto 21) – The application now runs on Java 21. Ensure you have Amazon Corretto 21 (or any OpenJDK 21 distribution) installed.
- **Maven 3.x** – For building the project.
- (Optional) Docker – To build and run the service using the Docker container.

## Building the Application

To build the project, make sure you have JDK 21 installed. Then run:

```bash
mvn clean package
