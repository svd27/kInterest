package info.kinterest.datastores.tests.unrelated

import info.kinterest.datastores.tests.relations.jvm.PersonJvm
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@InternalSerializationApi
class SerializerTest : FreeSpec({

    "given a RelationTo" - {
        val str = Json.encodeToString(Int.serializer(), 5)
        println(str)
        val n = Json.decodeFromString(Int.serializer(), str)
        n.shouldBe(5)
        val s1 = Json.encodeToString(PersonJvm.AGE.serializer(), 4)
        s1.shouldBe("4")
    }
})