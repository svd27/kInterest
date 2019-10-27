package info.kinterest.datastores.tests.containers

import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.client.config.ClientNetworkConfig
import com.hazelcast.client.config.YamlClientConfigBuilder
import com.hazelcast.config.Config
import com.hazelcast.config.SerializerConfig
import com.hazelcast.internal.serialization.impl.JavaDefaultSerializers
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.kodein.di.bindings.ScopeCloseable
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

class HazelcastClusterContainer(val version:String="latest") : ScopeCloseable {
    val log = KotlinLogging.logger {  }
    val containers : List<KGenericContainer>

    init {
        log.info { "files: ${Files.list(Paths.get(".")).collect(Collectors.toList())}" }
        val nw = Network.newNetwork()

        val image = "hazelcast/hazelcast:$version"
        val m1 = KGenericContainer(image)
                .withNetwork(nw)
                .withNetworkAliases("HZ1")
                .withExposedPorts(5701)
                .withCopyFileToContainer(MountableFile.forClasspathResource("hazelcast-cluster.xml"), "/opt/hazelcast/hazelcast-cluster.xml")
                .withEnv("JAVA_OPTS", "-Dhazelcast.config=/opt/hazelcast/hazelcast-cluster.xml")
                .waitingFor(Wait.forLogMessage("""INFO:.*:5701.*:5701 is STARTED.*\n""", 1))
        val m2 = KGenericContainer(image)
                .withNetwork(nw)
                .withNetworkAliases("HZ2")
                .withExposedPorts(5701)
                .withCopyFileToContainer(MountableFile.forClasspathResource("hazelcast-cluster.xml"), "/opt/hazelcast/")
                .withEnv("JAVA_OPTS", "-Dhazelcast.config=/opt/hazelcast/hazelcast-cluster.xml")
                .waitingFor(Wait.forLogMessage("""INFO:.*:5701.*:5701 is STARTED.*\n""", 1))

        val m3 = KGenericContainer(image)
                .withNetwork(nw)
                .withNetworkAliases("HZ3")
                .withExposedPorts(5701)
                .withCopyFileToContainer(MountableFile.forClasspathResource("hazelcast-cluster.xml"), "/opt/hazelcast/")
                .withEnv("JAVA_OPTS", "-Dhazelcast.config=/opt/hazelcast/hazelcast-cluster.xml")
                .waitingFor(Wait.forLogMessage("""INFO:.*:5701.*:5701 is STARTED.*\n""", 1))

        runBlocking(newFixedThreadPoolContext(3, "hzpool")) {
            val as1 = async { log.debug { "start m1" }
                m1.start()
            }
            val as2 = async { log.debug { "start m2" }
                m2.start()
            }
            val as3 = async { log.debug { "start m3" }
                m3.start()
            }
            as1.await()
            as2.await()
            as3.await()
        }

        containers = listOf(m1, m2, m3)
        val client = HazelcastClient.newHazelcastClient(
                ClientConfig().setNetworkConfig(ClientNetworkConfig()
                        .addAddress("${m1.containerIpAddress}:${m1.firstMappedPort}")
                        .addAddress("${m2.containerIpAddress}:${m2.firstMappedPort}")
                        .addAddress("${m3.containerIpAddress}:${m3.firstMappedPort}")
                ))
        log.info {"name: ${client.distributedObjects}"}
        val al = client.cpSubsystem.getAtomicLong("amen")
        al.set(1)
        runBlocking {
            log.info { "al: ${al.addAndGetAsync(1).get()}" }
        }
    }

    val host = containers.first().containerIpAddress
    val port = containers.first().firstMappedPort

    val addresses : List<String> = containers.map { "${it.containerIpAddress}:${it.firstMappedPort}" }

    override fun close() {
        log.info { "closing Hazelcast" }
        containers.forEach { it.stop() }
    }
}