package com.arcadiaeditions.config

import java.io.File
import java.io.IOException
import java.net.Socket
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait

class DockerComposeInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    /** Use this annotation to activate TestContainers in your test. */
    @kotlin.annotation.Target(kotlin.annotation.AnnotationTarget.CLASS)
    @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.RUNTIME)
    @org.springframework.test.context.ContextConfiguration(initializers = [DockerComposeInitializer::class])
    annotation class EnableDockerCompose

    private data class Service(
        val name: String,
        val port: Int,
        val hostPort: Int,
        val envVar: String,
        val envValueTemplate: String,
    )

    companion object {
        /** Test-only compose: own project name and host ports (15432, 29092, 22181, 18081), not docker-compose.yml. */
        private const val DOCKER_COMPOSE_FILE = "./docker-compose-test.yml"
        private val SERVICES =
            listOf(
                Service("postgresql", 5432, 15432, "DATASOURCE_URL", "jdbc:postgresql://%s:%s/app"),
                Service("kafka", 9092, 29092, "KAFKA_BOOTSTRAP_SERVERS", "%s:%s"),
            )

        val HOST: String = DockerClientFactory.instance().dockerHostIpAddress()
        val container: ComposeContainer = ComposeContainer(File(DOCKER_COMPOSE_FILE)).withEnv("HOST", HOST)

        init {
            for (service in SERVICES) {
                if ("schema-registry" == service.name) {
                    container.withExposedService(
                        service.name,
                        service.port,
                        Wait.forHttp("/subjects").forStatusCode(200),
                    )
                } else {
                    container.withExposedService(service.name, service.port, Wait.forListeningPort())
                }
            }
        }

        var isContainerRunning = false
        private var properties: List<String> = emptyList()
    }

    override fun initialize(ctx: ConfigurableApplicationContext) {
        if (isContainerRunning) {
            log.info("Docker Compose Containers are already running from TestContainers. Skipping...")
        } else if (isTestComposeRunning()) {
            log.info(
                "Test compose {} is already running on {}. Skipping TestContainers...",
                DOCKER_COMPOSE_FILE,
                SERVICES.map { it.hostPort },
            )
            properties = SERVICES.map { "${it.envVar}=${String.format(it.envValueTemplate, "localhost", it.hostPort)}" }
        } else {
            log.info("Test compose is not running. Starting {} from TestContainers...", DOCKER_COMPOSE_FILE)
            container.start()
            isContainerRunning = true
            properties =
                SERVICES.map { service ->
                    val port = container.getServicePort(service.name, service.port)
                    log.info("DockerCompose exposed port for {}: {}", service.name, "$HOST:$port")
                    log.info("DockerCompose Service {} listening: {}", service.name, isPortOpen(HOST, port))
                    "${service.envVar}=${String.format(service.envValueTemplate, HOST, port)}"
                }
        }
        TestPropertyValues.of(properties).applyTo(ctx.environment)
    }

    /**
     * True when the test compose was started once by hand, to be reused by every IT run:
     * `docker compose -f docker-compose-test.yml -p arcadia-kotlin-it up -d`. Checked by its own host ports (15432,
     * 29092), never the dev docker-compose.yml ones (5432, 9092).
     */
    private fun isTestComposeRunning(): Boolean = SERVICES.all { isPortOpen("localhost", it.hostPort) }

    fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket(host, port).use { true }
        } catch (e: IOException) {
            false
        }
    }
}
