package info.kinterest.docker.hazelcast.jet

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.client.config.ClientNetworkConfig
import com.hazelcast.config.GroupConfig
import info.kinterest.docker.client.DockerClientConfigProvider
import io.kotlintest.specs.FreeSpec
import mu.KotlinLogging
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class HazelcastJetClusterTest : FreeSpec({
    val log = KotlinLogging.logger { }
    val cmds: DockerCmdExecFactory = NettyDockerCmdExecFactory()
    val cfg = DockerClientConfigProvider.config()
    val client = DockerClientBuilder.getInstance(cfg)
            .withDockerCmdExecFactory(cmds)
            .build()

    "given a docker client" - {
        "a jet cluster should successfully start" - {
            val jet = HazelcastJetCluster(client).apply { start() }
            "a hazelcast client can successfully connect" - {
                val hzcl = HazelcastClient.newHazelcastClient(
                        ClientConfig()
                                .setNetworkConfig(
                                        ClientNetworkConfig().setAddresses(jet.ips).setSmartRouting(false)
                                ).setGroupConfig(GroupConfig("jet"))
                )
                hzcl.distributedObjects.forEach { log.debug { "distributed object $it" } }
                "and access the cpSubsystem" - {
                    val al = hzcl.cpSubsystem.getAtomicLong("x")
                    al.set(1L)
                    al.addAndGet(2)
                    expectThat(al.get()).isEqualTo(3)
                }
            }
        }
    }
})