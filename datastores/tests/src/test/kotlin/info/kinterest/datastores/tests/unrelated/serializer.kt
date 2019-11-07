package info.kinterest.datastores.tests.unrelated

import info.kinterest.datastores.tests.relations.jvm.PersonJvm
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@UnstableDefault
@ImplicitReflectionSerializer
class SerializerTest : FreeSpec({

    "given a RelationTo" - {
        val str = Json.stringify(Int.serializer(), 5)
        println(str)
        val n = Json.parse(Int.serializer(), str)
        n.shouldBe(5)
        val s1 = Json.stringify(PersonJvm.AGE.serializer(), 4)
        s1.shouldBe("4")
    }
})