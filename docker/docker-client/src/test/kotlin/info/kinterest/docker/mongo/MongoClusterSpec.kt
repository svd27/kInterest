package info.kinterest.docker.mongo

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import io.kotlintest.specs.FreeSpec
import mu.KotlinLogging

class MongoClusterSpec() : FreeSpec({
    val log = KotlinLogging.logger { }
    val cmds: DockerCmdExecFactory = NettyDockerCmdExecFactory()
    val cfg = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost("tcp://localhost:2375")
            .withDockerTlsVerify(false)
            .build()
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