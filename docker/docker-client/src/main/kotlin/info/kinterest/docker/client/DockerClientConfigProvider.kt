package info.kinterest.docker.client

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig

object DockerClientConfigProvider {
    fun config() : DockerClientConfig {
        val cfgb = DefaultDockerClientConfig.createDefaultConfigBuilder()
        val getenv = System.getenv("DOCKER_HOST")
        if(getenv != null) {
            cfgb.withDockerHost(getenv)
        }
        return cfgb.build()
    }
}