# Bedreflyt Digital Twin Lifecycle Manager

A Spring Boot-based REST API service for managing hospital ward capacity allocation and digital twin lifecycle operations. This service is part of the Bedreflyt system, designed to optimize patient room allocation in hospital wards.

## Overview

The Bedreflyt Lifecycle Manager provides intelligent decision-making capabilities for hospital ward management, including:

- **Ward State Management**: Monitor and track ward capacity states (full/not full)
- **Room Allocation**: Automatically determine optimal room allocations for incoming patients through the invocation of the Z3 solver
- **Integration**: Communicates with external services for room data and solver algorithms

## Technology Stack

- **Language**: Kotlin 2.0.0
- **Framework**: Spring Boot 3.4.2
- **Java Version**: 21
- **Build Tool**: Gradle 8.x
- **API Documentation**: SpringDoc OpenAPI 2.8.4
- **Security**: Spring Security with JWT (0.12.6)
- **Serialization**: Jackson 2.18.2
- **Containerization**: Docker

## Project Structure

```
src/main/kotlin/no/uio/bedreflyt/lm/
├── Main.kt                      # Application entry point
├── config/                      # Configuration classes
│   ├── AppConfig.kt
│   ├── EnvironmentConfig.kt
│   ├── SchedulingConfig.kt
│   ├── SecurityConfig.kt
│   └── WebConfig.kt
├── controller/                  # REST API controllers
│   └── StateController.kt       # Ward state and allocation endpoints
├── model/                       # Domain models
│   └── HospitalWard.kt         # Ward model
├── service/                     # Business logic services
│   ├── AllocationService.kt    # Patient allocation logic
│   ├── CorridorService.kt      # Corridor management
│   ├── OfficeService.kt        # Office room management
│   ├── PatientAllocationService.kt
│   ├── StateService.kt         # Ward state management
│   ├── TreatmentRoomService.kt
│   └── WardService.kt          # Ward data retrieval
├── tasks/                       # Scheduled tasks and complex operations
│   └── DecisionTask.kt         # Decision-making algorithms
└── types/                       # Type definitions and DTOs
    ├── Types.kt                # Data transfer objects
    └── WardState.kt            # Ward state enum
```

## Configuration

### Environment Variables

The application can be configured using the following environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `API_PORT` | Application server port | `8091` |
| `BEDREFLYT_API` | Bedreflyt API host | `localhost` |
| `BEDREFLYT_PORT` | Bedreflyt API port | `8090` |
| `SOLVER_API` | Solver service host | `localhost` |
| `SOLVER_PORT` | Solver service port | `8000` |

### Application Properties

Located in `src/main/resources/application.properties`:

```properties
server.port=${API_PORT:8091}
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
```

## Building the Application

### Prerequisites

- Java 21 or higher
- Gradle 8.x (or use the included wrapper)

### Build Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Create executable JAR
./gradlew bootJar
```

The executable JAR will be created in `build/libs/bedreflyt-lm-0.2.0.jar`

## Running the Application

### Local Development

```bash
# Using Gradle
./gradlew bootRun

# Using built JAR
java -jar build/libs/bedreflyt-lm-0.2.0.jar
```

### Docker

```bash
# Build the Docker image
docker build -t bedreflyt-lm:0.2.0 .

# Run the container
docker run -p 8091:8091 \
  -e BEDREFLYT_API=bedreflyt-api-host \
  -e SOLVER_API=solver-host \
  bedreflyt-lm:0.2.0
```

## API Documentation

Once the application is running, access the interactive API documentation at:

- **Swagger UI**: http://localhost:8091/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8091/api-docs

## Architecture

### Decision-Making Algorithm

The `DecisionTask` component implements the core allocation logic:

1. **Capacity Assessment**: Evaluates current ward capacity vs. incoming patients
2. **Available Resources**: Identifies available rooms (corridors, offices, treatment rooms)
3. **Penalty Calculation**: Computes penalties for opening different room types
4. **Optimal Selection**: Chooses the most cost-effective room allocation strategy
5. **Integration**: Communicates with external solver service for complex optimization

### Services

- **StateService**: Manages ward states in concurrent hash map
- **AllocationService**: Retrieves and processes patient allocation data
- **WardService**: Fetches ward configuration from external API
- **CorridorService**: Manages corridor availability and data
- **OfficeService**: Handles office room availability
- **TreatmentRoomService**: Manages treatment room data

## Development

### Code Style

- Written in Kotlin with functional programming patterns
- Uses Spring Boot annotations for dependency injection
- RESTful API design principles
- Comprehensive Swagger/OpenAPI documentation

### Security

The application includes Spring Security configuration with:
- JWT token-based authentication
- Secured API endpoints
- Configurable security policies

### Logging

The application uses SLF4J with simple logger for:
- Request tracking
- Decision-making process logging
- Error reporting

## Dependencies

Key dependencies include:

- Spring Boot Starter Web
- Spring Boot Starter WebFlux
- Spring Boot Starter Security
- SpringDoc OpenAPI
- Jackson (JSON processing)
- JWT (JSON Web Tokens)
- Kotlin standard library

See [build.gradle](build.gradle) for complete dependency list.

## Version

Current version: **0.2.0**

## License

This project is licensed under the BSD 3-Clause License. See [LICENSE](LICENSE) for details.
