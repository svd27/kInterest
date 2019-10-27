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
    fun testCreate() {
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
        client.pullImageCmd("hazelcast/hazelcast:latest").exec(object : PullImageResultCallback() {
            override fun onNext(item: PullResponseItem?) {
                log.info { "image: $item"  }
            }

            override fun onError(throwable: Throwable?) {
                log.error(throwable) {}
            }
        })

        val images = client.listImagesCmd().withImageNameFilter("hazelcast/hazelcast:latest").exec()
        images.forEach {
            log.info { "Image: ${it.id} $it" }
        }
        val lcresp = client.listContainersCmd().withShowAll(true).withNameFilter(listOf("test1")).exec().apply {
            log.info { "listContainers: ${this.size}: $this" }
        }
        val cid = if(lcresp.isEmpty())
          client.createContainerCmd("hazelcast/hazelcast:latest").withName("test1").withExposedPorts(listOf(ExposedPort(5701))).exec().apply { log.info { this } }.id
        else lcresp[0].id
        log.info { "created ${cid}"  }
        client.startContainerCmd("test").withContainerId(cid)
        val logCallback = object : LogContainerResultCallback() {
            override fun onNext(item: Frame?) {
                log.info { item }
            }
        }
        client.logContainerCmd(cid).withStdOut(true).exec(logCallback)
        logCallback.awaitStarted()
        runBlocking { delay(8000) }
    }
}