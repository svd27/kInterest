import mu.KotlinLogging
import org.junit.jupiter.api.Test
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import strikt.api.expectThat
import strikt.assertions.atLeast
import strikt.assertions.contains
import strikt.assertions.hasSize
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.streams.asSequence

class ResourceLoaderTest : Spek({
    val log = KotlinLogging.logger {  }
    describe("resource loader") {
        val res = this::class.java.classLoader.getResources("a/b/test.res")

        it("finds all resources") {
            val res = res.toList()
            expectThat(res) {
                hasSize(2)
            }
            res.forEach {
                val r = BufferedReader(InputStreamReader(it.openStream()))
                val lines = r.lines().asSequence().map { it.trim() }.toList()
                expectThat(lines) {
                    hasSize(1)
                }
                log.info { "$it:\n$lines" }
                expectThat(lines) {
                    hasSize(1)
                }
            }
        }
    }
})