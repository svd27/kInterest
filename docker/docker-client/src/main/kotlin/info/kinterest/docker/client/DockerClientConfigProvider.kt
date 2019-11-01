package info.kinterest.docker.client

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.netty.NettyDockerCmdExecFactory

object DockerClientConfigProvider {
    val localOverride : String?

    init {
        localOverride = if("windows" in System.getProperty("os.name")?.toLowerCase()?:"") {
            "tcp://127.0.0.1:2375"
        } else null
    }
    fun config() : DockerClientConfig {
        val cfgb = DefaultDockerClientConfig.createDefaultConfigBuilder()

        val getenv = System.getenv("DOCKER_HOST")
        if(getenv != null) {
            cfgb.withDockerHost(getenv)
        }

        if(localOverride!=null) {
            cfgb.withDockerHost(localOverride)
        }
        return cfgb.build()
    }

    fun client() : DockerClient {
        val cmds: DockerCmdExecFactory = NettyDockerCmdExecFactory()
        val cfg = config()
        return DockerClientBuilder.getInstance(cfg)
                .withDockerCmdExecFactory(cmds)
                .build()
    }
}