package info.kinterest.docker.client

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.LogContainerResultCallback
import com.github.dockerjava.core.command.PullImageResultCallback
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.Closeable
import java.lang.Exception
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestDockerClient {
    val log = KotlinLogging.logger { }
    lateinit var client : DockerClient

    @BeforeAll
    fun setUp() {
        val cmds: DockerCmdExecFactory = NettyDockerCmdExecFactory()
        val cfg = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2375")
                .withDockerTlsVerify(false)
                .build()
        client = DockerClientBuilder.getInstance(cfg)
                .withDockerCmdExecFactory(cmds)
                .build()
    }

    @Test
    fun testListImages() {
        val info = client.infoCmd().exec()
        log.info { "docker info: $info" }
        client.listImagesCmd().exec().apply {
            log.info { this }
        }
    }

    @Test
    fun testCreateNetwork() {
        val listResp = client.listNetworksCmd().withNameFilter("nw1").exec()
        log.info { listResp }
        val nws = if(listResp.isEmpty())
            listOf(
                    client.createNetworkCmd().withName("nw1").withCheckDuplicate(true).exec().apply { log.info { this } }.id
            )
        else
            listResp.map { it.id }
        log.info { nws }
        log.info {
            nws.map {
                client.removeNetworkCmd("nw1").withNetworkId(it).exec()
            }
        }
    }

    @Test
    fun runTest() {
        val searchImagesCmdResp = client.searchImagesCmd("hazelcast").exec()
        log.info {
            searchImagesCmdResp.map { "${it.name} ${it.description}" }.joinToString("\n")
        }

        log.info { "listing..." }
        val images = client.listImagesCmd().withImageNameFilter("hazelcast/hazelcast").exec()
        images.forEach {
            log.info { "Image: ${it.id} ${it.repoTags.toList()}" }
        }

        if(images.none { it.repoTags.any { it == "hazelcast/hazelcast:latest" } }) {
            log.info { "pulling" }
            val picb = object : PullImageResultCallback() {
                override fun onNext(item: PullResponseItem?) {
                    log.info { "image: $item" }
                }

                override fun onError(throwable: Throwable?) {
                    log.error(throwable) { "pull error" }
                }
            }
            client.pullImageCmd("hazelcast/hazelcast").withTag("latest").exec(picb)
            picb.awaitCompletion()
        }


        val cid =  client.createContainerCmd("hazelcast/hazelcast:latest").withExposedPorts(listOf(ExposedPort(5701))).withTty(false).exec().apply { log.info { this } }.id

        log.info { "created ${cid}"  }
        if(client.inspectContainerCmd(cid).exec().apply {
            log.info { "${this.id} running ${state.running} health: ${state.health}" }
        }.state?.running != true) {
            client.startContainerCmd("").withContainerId(cid).exec()
            log.info { "starting $cid" }
        }
        val logCallback = object : LogContainerResultCallback() {
            override fun onNext(item: Frame?) {
                log.info { "log: $item" }
                if(item!=null && item.toString().matches(".*is STARTED.*".toRegex()))
                    log.info { "$item matches" }
            }
        }
        client.logContainerCmd(cid).withStdOut(true).withStdErr(true).withFollowStream(true).withSince(0).exec(logCallback)
        logCallback.awaitCompletion(90, TimeUnit.SECONDS)
    }
}