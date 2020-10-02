package info.kinterest.docker.mongo

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import info.kinterest.docker.client.DockerClientConfigProvider
import io.kotest.core.spec.style.FreeSpec
import mu.KotlinLogging

class MongoClusterSpec() : FreeSpec({
    val log = KotlinLogging.logger { }
    val cmds: DockerCmdExecFactory = NettyDockerCmdExecFactory()
    val cfg = DockerClientConfigProvider.config()
    val client = DockerClientBuilder.getInstance(cfg)
            .withDockerCmdExecFactory(cmds)
            .build()
    "starting a mongocluster" - {
        val mongoCluster = MongoCluster(client)
        mongoCluster.start()
        mongoCluster.containers.forEach {
            log.debug { it.key.logs.joinToString("\n") }
        }
    }
})