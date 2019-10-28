package info.kinterest.docker.client

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.ExecStartResultCallback
import com.github.dockerjava.core.command.LogContainerResultCallback
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import info.kinterest.functional.Try
import info.kinterest.functional.getOrDefault
import info.kinterest.functional.getOrElse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import strikt.api.expectThat
import strikt.assertions.isTrue
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaseContainerTest {
    val log = KotlinLogging.logger { }
    lateinit var client : DockerClient
    var networks : Set<String> = emptySet()
    var containers : Set<String> = emptySet()

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


    @AfterAll
    fun tearDown() {
        containers.forEach {
            Try { client.stopContainerCmd(it).exec(); }.getOrElse { log.warn(it) {} }
            Try {client.removeContainerCmd(it).withRemoveVolumes(true).withForce(true).exec() }.getOrElse { log.warn(it) {} }
        }

        networks.forEach {
            Try {client.removeNetworkCmd(it).exec()}.getOrElse { log.warn(it) {} }
        }
    }

    @Test
    fun withPull() {
        client.listImagesCmd().withImageNameFilter("hello-world").exec().forEach {
            if(it.repoTags.any { it=="hello-world:latest" }) {
                client.removeImageCmd(it.id).withForce(true)
            }
        }

        val container = BaseContainer(client, "hello-world")
        containers += container.container

        container.start(LogWaitStrategy(Duration.ofSeconds(5), LogAcceptor.string("https://docs.docker.com/get-started/")))
    }

    @Test
    fun hazelcastClusterNoNw() {
        val container1 = BaseContainer(client, "hazelcast/hazelcast", env = listOf("JAVA_OPTS=-Dhazelcast.config=/opt/cluster/hazelcast-cluster.xml"),
                binds = listOf("/opt/cluster" to listOf(javaClass.classLoader.getResource("hazelcast-cluster.xml"))))
        containers += container1.container
        val container2 = BaseContainer(client, "hazelcast/hazelcast")
        containers += container2.container
        val timeout = Duration.ofSeconds(10)
        container1.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        container2.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        val lcb = object : LogContainerResultCallback() {
            override fun onNext(item: Frame?) {
                log.info { "$item" }
            }
        }
        log.info { "Logging container1 {${container1.container}}:" }
        client.logContainerCmd(container1.container).withFollowStream(false).withStdOut(true).withStdErr(true).exec(lcb)
        lcb.awaitCompletion()
        log.info { "Logging container2 {${container2.container}}:" }
        client.logContainerCmd(container2.container).withFollowStream(false).withStdOut(true).withStdErr(true).exec(lcb)
        lcb.awaitCompletion()
    }

    @Test
    fun hazelcastNetworkNoCluster() {
        val nw1 = client.createNetworkCmd().withDriver("bridge").withName("nw1").exec().id
        val nw2 = client.createNetworkCmd().withDriver("bridge").withName("nw2").exec().id
        networks += listOf(nw1, nw2)
        log.info { "nw1: $nw1 nw2: $nw2" }
        val container1 = BaseContainer(client, "hazelcast/hazelcast", network = nw1)
        val container2 = BaseContainer(client, "hazelcast/hazelcast", network = nw2, binds = listOf("/opt/cluster" to listOf(javaClass.classLoader.getResource("hazelcast-cluster.xml"))))
        //container1.copyResourceToContainer("hazelcast-cluster.xml", javaClass.classLoader.getResource("hazelcast-cluster.xml"), "/opt/hazelcast/")

        val timeout = Duration.ofSeconds(10)
        container1.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        containers += container1.container
        container2.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        containers += container2.container
        val lcb = object : LogContainerResultCallback() {
            override fun onNext(item: Frame?) {
                log.info { "$item" }
            }
        }

        var res : String = ""
        val execcb = object : ExecStartResultCallback() {
            override fun onNext(frame: Frame?) {
                res += frame.toString()
                res += "\n"
            }
        }

        val cmdLsId = client.execCreateCmd(container2.container).withAttachStdout(true).withAttachStderr(true).withCmd("ls -l", "/opt/cluster/").exec().id
        res = ""
        client.execStartCmd(container2.container).withExecId(cmdLsId).exec(execcb)
        execcb.awaitCompletion()
        log.info { "ls returned:\n$res" }

        val cmdCatId = client.execCreateCmd(container2.container).withAttachStdout(true).withAttachStderr(true).withCmd("cat", "/opt/cluster/hazelcast-cluster.xml").exec().id
        res = ""

        client.execStartCmd(container2.container).withExecId(cmdCatId).exec(execcb)
        execcb.awaitCompletion()
        log.info { "cat returned:\n$res" }
    }

    @Test
    fun hazelCastCpCluster() {
        val nw1 = client.createNetworkCmd().withDriver("bridge").withName("nw1").exec().id.apply { networks += this }
        fun createContainer() = BaseContainer(client, "hazelcast/hazelcast", network = nw1, binds = listOf("/opt/cluster" to listOf(javaClass.classLoader.getResource("hazelcast-cluster.xml"))), env = listOf("JAVA_OPTS=-Dhazelcast.config=/opt/cluster/hazelcast-cluster.xml")).apply { containers += container }
        val c1 = createContainer()
        val c2 = createContainer()
        val c3 = createContainer()

        val timeout = Duration.ofSeconds(15L)
        fun start(c:BaseContainer) = c.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        start(c1)
        start(c2)
        start(c3)

        logContainer(c1)
        c1.waitForLog(LogAcceptor.regex(".*CPMember.*uuid=.*, address=.*:.*- LEADER.*".toRegex()), timeout)
    }

    @Test
    fun hazelcastNetworkTwoCluster() {
        val nw1 = client.createNetworkCmd().withDriver("bridge").withName("nw1").exec().id
        val nw2 = client.createNetworkCmd().withDriver("bridge").withName("nw2").exec().id
        networks += listOf(nw1, nw2)
        log.info { "nw1: $nw1 nw2: $nw2" }
        val container1 = BaseContainer(client, "hazelcast/hazelcast", network = nw1)
        containers += container1.container
        val container2 = BaseContainer(client, "hazelcast/hazelcast", network = nw1)
        containers += container2.container
        val container3 = BaseContainer(client, "hazelcast/hazelcast", network = nw2)
        containers += container3.container
        val container4 = BaseContainer(client, "hazelcast/hazelcast", network = nw2)
        containers += container4.container
        val timeout = Duration.ofSeconds(10)
        container1.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        container2.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        container3.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        container4.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))

        logContainer(container1)
        logContainer(container2)
        logContainer(container3)
        logContainer(container4)
    }

    fun logContainer(container: BaseContainer) {
        val lcb = object : LogContainerResultCallback() {
            override fun onNext(item: Frame?) {
                log.info { "$item" }
            }
        }

        log.info { "Logging container1 {${container.container}}:" }
        client.logContainerCmd(container.container).withFollowStream(false).withStdOut(true).withStdErr(true).exec(lcb)
        lcb.awaitCompletion()
    }

    @Test
    fun execTest() {
        val container1 = BaseContainer(client, "hazelcast/hazelcast")
        containers += container1.container
        container1.start(LogWaitStrategy(Duration.ofSeconds(15), LogAcceptor.string("is STARTED")))
        val res = container1.exec(listOf("ls", "/opt/"), duration = Duration.of(5, ChronoUnit.SECONDS))
        expectThat(res.isSuccess).isTrue()
        expectThat(res.getOrDefault { listOf() }.any { "hazelcast" in it }).isTrue()
    }
}
