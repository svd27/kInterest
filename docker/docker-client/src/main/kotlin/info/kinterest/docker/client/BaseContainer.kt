package info.kinterest.docker.client

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.LogContainerCmd
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.command.ExecStartResultCallback
import com.github.dockerjava.core.command.LogContainerResultCallback
import com.github.dockerjava.core.command.PullImageResultCallback
import info.kinterest.functional.Try
import info.kinterest.functional.getOrDefault
import info.kinterest.functional.getOrElse
import info.kinterest.functional.suspended
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

sealed class LogAcceptor {
    abstract fun accept(s: String): Boolean
    class RegexAcceptor(private val regex: Regex) : LogAcceptor() {
        override fun accept(s: String) = regex.matches(s)
    }

    class StringAcceptor(private val string: String) : LogAcceptor() {
        override fun accept(s: String) = s.contains(string)
    }

    companion object {
        fun regex(regex: Regex): LogAcceptor = RegexAcceptor(regex)
        fun string(s: String): LogAcceptor = StringAcceptor(s)
    }
}

sealed class Waiter {
    private val log = KotlinLogging.logger { }
    suspend fun waitFor(duration: java.time.Duration) {
        Try.suspended {
            log.debug { "starting with timeout $duration" }
            withTimeout(duration.toMillis()) {
                waiter()
            }
            log.debug { "finished timeout" }
        }.apply {
            done()
        }.getOrElse {
            //log.warn(it) {  }
        }
    }

    abstract suspend fun waiter()
    abstract fun done()
}

class LogWaiter(logContainerCmd: LogContainerCmd, acceptor: LogAcceptor) : Waiter() {
    private val log = KotlinLogging.logger { }
    private var found = false
    private var completed = false
    private val logContainerCmd: LogContainerCmd = logContainerCmd.withStdErr(true).withStdOut(true).withSince(0).withFollowStream(true)
    val cb: LogContainerResultCallback = object : LogContainerResultCallback() {
        override fun onNext(item: Frame?) {
            log.trace { "checking $item" }
            if (acceptor.accept(item.toString())) {
                log.trace { "accepted $item" }
                found = true
                completed = true
                this.close()
            }
        }

        override fun onComplete() {
            log.debug { "complete" }
            completed = true
            if (!found)
                logContainerCmd.exec(this)
        }
    }

    override suspend fun waiter() {
        logContainerCmd.exec(cb)
        while (!completed) {
            delay(100)
        }

    }

    override fun done() {
        completed = true
        cb.close()
        if (!found) throw IllegalStateException()
    }
}


sealed class WaitStrategy(val duration: java.time.Duration)
class LogWaitStrategy(duration: java.time.Duration, val acceptor: LogAcceptor) : WaitStrategy(duration)

class BaseContainer(val client: DockerClient, val image: String, val version: String = "latest",
                    exposedPorts: ExposedPorts? = null,
                    portBindings: List<PortBinding> = listOf(),
                    cmd : List<String> = emptyList(),
                    env : List<String> = emptyList(),
                    val network: String? = null,
                    aliases: List<String>? = null,
                    binds: Iterable<Pair<String,List<URL>>> = listOf()) {
    private val log = KotlinLogging.logger { }
    private val fullImageName = "$image:$version"
    val imageId: String
    val container: String

    init {
        var imgId: String? = null
        var container: String? = null
        Try {
            imgId = client.listImagesCmd().withImageNameFilter(image).exec().firstOrNull { it.repoTags.any { it == fullImageName || it == version } }?.id
            if (imgId == null) {
                val pullCallback = object : PullImageResultCallback() {
                    override fun onNext(item: PullResponseItem?) {
                        if (item != null && item.isPullSuccessIndicated) {
                            imgId = item.id
                        }
                    }
                }
                client.pullImageCmd(image).withTag(version).exec(pullCallback)
                pullCallback.awaitCompletion()
            }
            val createCommand = client.createContainerCmd(imgId!!)
            if(cmd.isNotEmpty()) {
                createCommand.withCmd(cmd).withArgsEscaped(false)
            }

            if(env.isNotEmpty())
            createCommand.withEnv(env)

            if (exposedPorts != null) createCommand.withExposedPorts(exposedPorts.exposedPorts.asList())
            @Suppress("DEPRECATION")
            if(portBindings.isNotEmpty()) createCommand.withPortBindings(portBindings)
            if(binds.firstOrNull()!=null) {
                @Suppress("DEPRECATION")
                createCommand.withBinds(createBinds(binds)).exec().apply {
                    warnings.forEach { log.warn { it } }
                }
            }
            if (aliases != null && aliases.toList().isNotEmpty()) {
                createCommand.withAliases(aliases.toList())
            }

            if (network != null) {
                val nwlresp = client.listNetworksCmd().withIdFilter(network).exec()
                if (nwlresp.isEmpty()) {
                    throw IllegalStateException()
                }
                @Suppress("DEPRECATION")
                createCommand.withNetworkMode(network)
            }
            container = createCommand.exec().apply {
                log.info { "created container ${this.id}" }
                warnings.forEach { log.warn { it } }
            }.id

            /*
            if (network != null) {
                val nwlresp = client.listNetworksCmd().withIdFilter(network).exec()
                if (nwlresp.isEmpty()) {
                    throw IllegalStateException()
                }
                client.connectToNetworkCmd().withContainerId(container!!).withNetworkId(network).withContainerNetwork(ContainerNetwork().withAliases(aliases)).exec()
            }
             */
        }.fold({
            container?.let { removeContainer(it) }
            throw it
        }) {
            Unit
        }
        imageId = imgId!!
        this.container = container!!
    }

    val ipAddress: String by lazy {
        "localhost"
        /*
        client.inspectContainerCmd(container).exec().run {
            networkSettings.networks[network]?.ipAddress ?: throw IllegalStateException()
        }
         */
    }

    val ports: Map<Int, Array<Ports.Binding>> by lazy {
        client.inspectContainerCmd(container).exec().run {
            networkSettings.ports.bindings.apply { log.debug { "ports.bindings: $this" } }.filter { it.value==null }.map { it.key.port to it.value }.toMap()
        }
    }

    fun createBinds(binds:Iterable<Pair<String,List<URL>>>) : List<Bind> = binds.map {
        val dir = it.second.fold(Files.createTempDirectory(".kinterest.docker").apply {
            toFile().deleteOnExit()
        }) {
            tmp, file ->
            log.info { file.path }
            val f = if(file.path.matches("/[A-Z]:/.*".toRegex())) Paths.get(file.path.substring(1)).fileName.toString() else
                Paths.get(file.toURI()).fileName.toString()
            file.openStream().copyTo(FileOutputStream(tmp.resolve(f).toFile()))
            tmp
        }.toAbsolutePath().toString()
        Bind(dir, Volume(it.first)).apply { log.debug { this } }
    }

    fun copyResourceToContainer(name:String, res:URL, remote:String) = Try {
        val tmp = Files.createTempDirectory(".kinterest.docker")
        tmp.toFile().deleteOnExit()
        res.openStream().copyTo(FileOutputStream(tmp.resolve(name).toFile()))
        client.copyArchiveToContainerCmd(container).withHostResource(tmp.toString()).withRemotePath(remote).withNoOverwriteDirNonDir(false).exec()
    }.getOrDefault { throw IllegalStateException(it) }

    fun start(waitStrategy: WaitStrategy? = null) {
        client.startContainerCmd(container).exec()
        when (waitStrategy) {
            is LogWaitStrategy -> {
                log.debug { "starting waiting" }
                waitForLog(waitStrategy.acceptor, waitStrategy.duration)
                log.debug { "done waiting" }
            }
        }
    }

    val logs : List<String>
      get() = kotlin.run {
          val res = mutableListOf<String>()
          val cb = object : LogContainerResultCallback() {
              override fun onNext(item: Frame?) {
                  res.add(item.toString())
              }
          }
          client.logContainerCmd(container).withFollowStream(false).withStdErr(true).withStdOut(true).withSince(0).exec(cb)
          cb.awaitCompletion()
          res
      }

    fun stopContainer() {
        if (client.inspectContainerCmd(container).withContainerId(container).exec().state.running.apply { log.debug { "state.running == $this" } } == true)
            Try { client.stopContainerCmd(container).exec() }.apply { removeContainer(container) }.fold({ IllegalStateException(it) }) { log.debug { "stopped ${container}" } }
    }

    private fun removeContainer(container: String) {
        runBlocking {
            withTimeout(5000) {
                while (client.inspectContainerCmd(container).withContainerId(container).exec().state.running.apply { log.debug { "state.running == $this" } } == true) { delay(100)}
            }
            client.removeContainerCmd(container).withForce(true).withRemoveVolumes(true).exec().apply { log.debug { "executed removeContainer" } }
        }

    }

    fun waitForLog(acc: LogAcceptor, duration: java.time.Duration) {
        runBlocking {
            LogWaiter(client.logContainerCmd(container), acc).waitFor(duration)
        }
    }


    fun exec(cmd:List<String>, stdout:Boolean=true, stderr:Boolean=true, duration: java.time.Duration?) : Try<List<String>> = Try {
        val cmdId = client.execCreateCmd(container).withCmd(*cmd.toTypedArray()).withAttachStdout(stdout).withAttachStderr(stderr).exec().id
        val res : MutableList<String> = mutableListOf()
        val cb = object : ExecStartResultCallback() {
            override fun onNext(frame: Frame?) {
                res += frame.toString()
            }
        }
        client.execStartCmd(cmdId).exec(cb)
        if(duration!=null)
          cb.awaitCompletion(duration.toMillis(), TimeUnit.MILLISECONDS)
        else cb.awaitCompletion()

        res
    }
}