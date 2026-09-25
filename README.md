# Arcadia Editions Modulith

Arcadia Editions Modulith


1. [Getting Started](#getting-started)
   1. [Prerequisites](#prerequisites)
2. [Technologies](#technologies)
   1. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
   1. [Clean/Hexagonal Architecture](#cleanhexagonal-architecture)
   2. [3-Tier Architecture](#3-tier-architecture)
   3. [Simple Domain Packaging](#simple-domain-packaging)
4. [API First](#api-first)
   1. [OpenAPI / SwaggerUI](#openapi--swaggerui)
      1. [Customization](#customization)
   2. [AsyncAPI / ZenWave SDK](#asyncapi--zenwave-sdk)
5. [Domain Modeling and Code Generation with ZenWave SDK](#domain-modeling-and-code-generation-with-zenwave-sdk)
   1. [Installing ZenWave SDK](#installing-zenwave-sdk)
   2. [Modeling and Generating Code](#modeling-and-generating-code)
6. [Testing](#testing)
   1. [Rules of thumb for Testing](#rules-of-thumb-for-testing)
7. [Code Formatting](#code-formatting)



After cloning the project, you can start the project with the following command:

```bash
docker-compose up -d
mvn spring-boot:run
```


* Java 25+ (Kotlin sources, compiled with `kotlin-maven-plugin`)
* Maven 3.6+
* Docker/Docker Compose
* Git and Git-Bash
* JBang and ZenWave SDK
* SDKMAN! (optional but highly recommended)


* Kotlin and Spring Boot 4.0.x
* Spring Data JPA or MongoDB
* Spring Data Elasticsearch
* Spring Cloud Streams for Kafka, RabbitMQ, or other Message Brokers
* Spring Security
* ZenWave SDK for Domain Modeling and Code Generation


This project is a blank canvas, you can follow a Clean/Hexagonal Architecture, Traditional 3-Tier Architecture, or a Simple Domain Packaging.


```
📦 <basePackage>
   📦 adapters
       └─ web
       |  └─ RestControllers (spring mvc)
       └─ events
          └─ *EventListeners (spring-cloud-streams)
   📦 core
       ├─ 📦 domain
       |     └─ (entities and aggregates)
       ├─ 📦 inbound
       |     ├─ dtos/
       |     └─ ServiceInterface (inbound service interface)
       ├─ 📦 outbound
       |     ├─ mongodb
       |     |  └─ *RepositoryInterface (spring-data interface)
       |     └─ jpa
       |        └─ *RepositoryInterface (spring-data interface)
       └─ 📦 implementation
             ├─ mappers/
             └─ ServiceImplementation (inbound service implementation)
   📦 infrastructure
     ├─ mongodb
     |  └─ CustomRepositoryImpl (spring-data custom implementation)
     └─ jpa
        └─ CustomRepositoryImpl (spring-data custom implementation)
```


```
📦 <basePackage>
   📦 web
       └─ RestControllers (spring mvc)
   📦 events
       └─ *EventListeners (spring-cloud-streams)
   📦 domain/model
       └─ (entities and aggregates)
   📦 service
       ├─ dtos/
       └─ ServiceInterface (inbound service interface)
       └─ 📦 implementation
           ├─ mappers/
           └─ ServiceImplementation (inbound service implementation)
   📦 repositories
       ├─ mongodb
       |   └─ CustomRepositoryImpl (spring-data custom implementation)
       └─ jpa
           └─ CustomRepositoryImpl (spring-data custom implementation)
```


Use a simple domain packaging for small projects with just one entity or aggregate, where you plan to have just one service, one repository, and one controller. Also useful for modular monoliths where each module follows a simple domain packaging.

``` 
📦 <basePackage>
   📦 domain/model (entities or aggregate)
   📦 dtos
   📦 mappers
   └─ RestController (spring mvc)
   └─ EventListener (spring-cloud-streams)
   └─ ServiceInterface (inbound service interface)
   └─ ServiceImplementation (inbound service implementation)
   └─ CustomRepositoryImpl (spring-data custom implementation)
```



The project uses `openapi-generator-maven-plugin` with the `kotlin-spring` generator (see pom.xml) to generate Kotlin SpringMVC interfaces and DTOs from the `src/main/resources/public/apis/openapi.yml` file.

Generated sources are placed in `target/generated-sources/openapi` which becomes a source folder for the project. To implement the API, you can create a new `@RestController` and implement the generated interface.


You can customize generated code with this properties in `pom.xml` or directly in the plugin `openapi-generator-maven-plugin`: 
```xml
<openApiApiPackage>${basePackage}.adapters.web</openApiApiPackage>
<openApiModelPackage>${basePackage}.adapters.web.model</openApiModelPackage>
```

SwaggerUI is available at http://localhost:8080/swagger-ui/index.html. If you need to add more OpenAPI files, you can customize SwaggerUI in `application.yml`:

```yaml
springdoc.swagger-ui.urls:
   - name: Arcadia Editions Modulith
     url: /apis/openapi.yml
```

URL is relative to 'src/main/resources/public'.


This project also uses `zenwave-sdk-maven-plugin` to generate (Java) interfaces, DTOs and producers/consumers using Spring Cloud Streams from the `src/main/resources/public/apis/asyncapi.yml` file.

If this file does not exist, code generation is skipped. Generated sources are placed in `target/generated-sources/zenwave` which becomes a source folder for the project.


You can customize generated code with this properties in `pom.xml` or directly in the plugin `zenwave-sdk-maven-plugin`:

```xml
<asyncApiModelPackage>${basePackage}.events.dtos</asyncApiModelPackage>
<asyncApiProducerApiPackage>${basePackage}.events.producer</asyncApiProducerApiPackage>
<asyncApiConsumerApiPackage>${basePackage}.events.consumer</asyncApiConsumerApiPackage>
```

See https://www.zenwave360.io/zenwave-sdk/plugins/asyncapi-spring-cloud-streams3/ for more information about API-First with AsyncAPI and ZenWave SDK.



ZenWave SDK allows you to focus on Domain Modeling and Analysis using a Domain Specific Language (ZenWave Domain Language or ZDL) to define your domain model. 

Then you can use ZenWave SDK command line or IntelliJ IDEA plugin to generate code from your analysed domain model.


Use JBang to install a self-updating evergreen version of ZenWave SDK Command Line.

```bash
jbang alias add --fresh --force --name=zw release@zenwave360/zenwave-sdk
```

This will give you access to the `jbang zw` command line tool. You can use `jbang zw --help` to see all available commands.

```bash
jbang zw --help list
```

Install [ZenWave Domain Editor for IntelliJ IDEA](https://plugins.jetbrains.com/plugin/22858-zenwave-domain-model-editor-for-zdl) from the marketplace.

See https://www.zenwave360.io/docs/getting-started/ for more information about ZenWave SDK.


The base project comes with to files:

* `zenwave-scripts.zdl`: sample scripts you can use from IntelliJ to run ZenWave commands and generate different software artifacts.
* `zenwave-model.zdl`: sample model you can use to generate a sample application and use it as a reference for your own modeling and analysis.


* JUnit5
* SpringWebTestClient for API Testing in Kotlin
* SpringBootTest for Integration Testing
* TestContainers with DockerComposeContainer for Integration Testing
* InMemory implementations of external Interfaces (Repositories) for Unit Testing (generated w/ ZenWave SDK)
* WireMock + OpenAPI Examples for Mocking external APIs
* Mockito as a last resort for Mocking external Dependencies. Whenever possible, please favor WireMock and InMemory Repositories.


- Favor functional Vertical Testing (Acceptance Testing) over unit testing where every dependency is mocked.
  - Use @SpringBootTests for the most important use cases.
  - Use InMemory dependencies for all use cases.
- Interfaces that connect to external resources **MUST** be tested using DockerComposeContainer, even if this does not contribute to code coverage metrics.



This project is configured to use [Spotless](https://github.com/diffplug/spotless) with [ktlint](https://pinterest.github.io/ktlint/) as code formatter. ZenWave SDK does not format the Kotlin code it generates, so apply formatting from the command line after generating code:

```bash
mvn spotless:apply
```

Keep a consistent code style from the beginning of the project.


Happy Coding!! 🚀🚀🚀
