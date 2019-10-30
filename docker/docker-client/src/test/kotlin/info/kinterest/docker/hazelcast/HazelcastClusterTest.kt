package info.kinterest.docker.hazelcast

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.client.config.ClientNetworkConfig
import info.kinterest.docker.client.DockerClientConfigProvider
import info.kinterest.functional.Try
import mu.KotlinLogging
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.time.Duration
import java.time.temporal.ChronoUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class HazelcastClusterTest {
    private val log = KotlinLogging.logger {  }
    lateinit var client : DockerClient
    @BeforeAll
    fun setUp() {
        log.info { System.getProperty("os.name") }
        log.info { System.getenv("DOCKER_OPTS") }
        log.info { System.getenv("DOCKER_HOST") }
        val cmds: DockerCmdExecFactory = NettyDockerCmdExecFactory()
        val cfg = DockerClientConfigProvider.config()
        client = DockerClientBuilder.getInstance(cfg)
                .withDockerCmdExecFactory(cmds)
                .build()
    }

    @Test
    fun testIpAddresses() {
        val cluster = HazelcastCluster(client = client, duration = Duration.of(25, ChronoUnit.SECONDS))
        cluster.start()
        cluster.containers.map {
            Try { log.debug { it.key.ipAddress } }
            Try {log.debug { "${it.key.ports}" }}

        }
        Try { log.debug { cluster.ips }}
        expectThat(cluster.ips) {
            hasSize(3)
        }
        cluster.ips.forEach { log.debug { it } }
    }

    @Test
    fun testClient() {
        val cluster = HazelcastCluster(client = client, duration = Duration.of(25, ChronoUnit.SECONDS))
        cluster.start()
        val hzcl = HazelcastClient.newHazelcastClient(ClientConfig().setNetworkConfig(ClientNetworkConfig().setAddresses(cluster.ips).setSmartRouting(false)))
        hzcl.distributedObjects.forEach { log.debug { "distributed object $it" } }
        val al = hzcl.cpSubsystem.getAtomicLong("x")
        al.set(1L)
        al.addAndGet(2)
        expectThat(al.get()).isEqualTo(3)
    }
}