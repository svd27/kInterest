package info.kinterest.datastores.hazelcast.jet

import com.hazelcast.core.HazelcastJsonValue
import com.hazelcast.function.BiConsumerEx
import com.hazelcast.function.ComparatorEx
import com.hazelcast.function.FunctionEx
import com.hazelcast.function.SupplierEx
import com.hazelcast.internal.json.Json
import com.hazelcast.internal.json.JsonValue
import com.hazelcast.jet.aggregate.AggregateOperation
import com.hazelcast.jet.aggregate.AggregateOperation1
import com.hazelcast.jet.datamodel.Tuple3
import com.hazelcast.nio.ObjectDataInput
import com.hazelcast.nio.ObjectDataOutput
import com.hazelcast.nio.serialization.DataSerializable
import com.hazelcast.projection.Projection
import java.io.Serializable

class FieldProjection(val fields: Set<String>) : Projection<MutableMap.MutableEntry<Any, HazelcastJsonValue>, Tuple3<Any, String, Map<String, JsonValue>>> {
    override fun transform(input: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Tuple3<Any, String, Map<String, JsonValue>> = run {
        val json = Json.parse(input.value.toString()).asObject()
        Tuple3.tuple3(input.key, json.get(FieldExtractor.METAINFO).asObject().getString(FieldExtractor.METAINFO_TYPE, ""), fields.map { it to json.get(it) }.toMap())
    }

    companion object {
        const val METAINFO: String = "_metaInfo"
        const val METAINFO_TYPE: String = "type"
        const val METAINFO_TYPES: String = "types"
    }
}

class FieldExtractor(val fields: Set<String>) : FunctionEx<MutableMap.MutableEntry<Any, HazelcastJsonValue>, Tuple3<Any, String, Map<String, JsonValue>>> {
    override fun applyEx(t: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Tuple3<Any, String, Map<String, JsonValue>> = run {
        val json = Json.parse(t.value.toString()).asObject()
        Tuple3.tuple3(t.key, json.get(METAINFO).asObject().getString(METAINFO_TYPE, ""), fields.map { it to json.get(it) }.toMap())
    }

    companion object {
        const val METAINFO: String = "_metaInfo"
        const val METAINFO_TYPE: String = "type"
        const val METAINFO_TYPES: String = "types"
    }
}

class GenericComparator(val fields: Set<Pair<String, String>>) : ComparatorEx<Tuple3<Any, String, Map<String, JsonValue>>>, Serializable {
    fun fieldCompare(f: String, c: String, c1: Map<String, JsonValue>, c2: Map<String, JsonValue>): Int = run {
        val f2 = c2.get(f)
        val f1 = c1.get(f)
        when {
            f1 == null || f1.isNull -> if (f2 == null || f2.isNull) 0 else -1
            f2 == null || f2.isNull -> 1
            else -> when (c) {
                "kotlin.Int" -> f1.asInt().compareTo(f2.asInt())
                "kotlin.Long" -> f1.asLong().compareTo(f2.asLong())
                "kotlin.String" -> f1.asString().compareTo(f2.asString())
                else -> throw IllegalArgumentException()
            }
        }
    }

    override fun compareEx(c1: Tuple3<Any, String, Map<String, JsonValue>>, c2: Tuple3<Any, String, Map<String, JsonValue>>): Int {
        fields.forEach {
            val cmp = fieldCompare(it.first, it.second, c1.f2(), c2.f2())
            if (cmp != 0) return cmp
        }
        return 0
    }

    companion object {
        @JvmStatic
        val serialVersionUID: Long = 1
    }

}

fun createPager(offset: Int, size: Int, sort: GenericComparator): AggregateOperation1<Tuple3<Any, String, Map<String, JsonValue>>, PageAggregation, PageAggregation> {
    val pager: AggregateOperation1<Tuple3<Any, String, Map<String, JsonValue>>, PageAggregation, PageAggregation> = AggregateOperation.withCreate(object : SupplierEx<PageAggregation> {
        override fun getEx(): PageAggregation {
            return PageAggregation(sort, mutableListOf(), offset, size, 0, 0)
        }
    }).andAccumulate(object : BiConsumerEx<PageAggregation, Tuple3<Any, String, Map<String, JsonValue>>> {
        override fun acceptEx(t: PageAggregation, u: Tuple3<Any, String, Map<String, JsonValue>>) {
            t.aggregate(u)
        }
    }).andCombine(object : BiConsumerEx<PageAggregation, PageAggregation> {
        override fun acceptEx(t: PageAggregation, u: PageAggregation) {
            t.eat(u)
        }
    }).andExportFinish(object : FunctionEx<PageAggregation, PageAggregation> {
        override fun applyEx(t: PageAggregation): PageAggregation = t.finish()
    })
    return pager
}


class PageAggregation(var sort: GenericComparator, var page: List<Tuple3<Any, String, Map<String, JsonValue>>>, var offset: Int, var size: Int, var dropped: Int, var finished:Int) : DataSerializable {
    constructor() : this(GenericComparator(setOf()), mutableListOf(), 0, 0, 0, 0)

    fun eat(pa: PageAggregation) {
        val nl = (page + pa.page).sortedWith(sort)
        page = nl.take(offset + size)
    }

    fun aggregate(t: Tuple3<Any, String, Map<String, JsonValue>>) {
        val nl = (page + t).sortedWith(sort)
        page = nl.take(offset + size)
    }

    fun finish(): PageAggregation = this.apply {
        finished = page.size
        if (page.size > offset) {
            dropped = offset
            page = page.drop(dropped)
        }
        page = page.take(size)
    }

    override fun writeData(out: ObjectDataOutput) {
        out.writeUTFArray(sort.fields.map { it.first }.toTypedArray())
        out.writeUTFArray(sort.fields.map { it.second }.toTypedArray())
        out.writeInt(page.size)
        page.forEach {
            out.writeObject(it.f0())
            out.writeUTF(it.f1())
            val entries = it.f2().entries.toList()
            out.writeUTFArray(entries.map { it.key }.toTypedArray())
            out.writeUTFArray(entries.map { it.value.toString() }.toTypedArray())
        }
        out.writeInt(offset)
        out.writeInt(size)
        out.writeInt(dropped)
        out.writeInt(finished)
    }

    override fun readData(inp: ObjectDataInput) {
        val names = inp.readUTFArray()
        val types = inp.readUTFArray()
        sort = GenericComparator(names.zip(types).toSet())
        val pageSize = inp.readInt()
        page = mutableListOf()
        repeat(pageSize) {
            val id = inp.readObject<Any>()
            val type = inp.readUTF()
            val keys = inp.readUTFArray()
            val values = inp.readUTFArray().map { Json.parse(it) }
            val map = keys.zip(values).toMap()
            page += Tuple3.tuple3(id, type, map)
        }
        offset = inp.readInt()
        size = inp.readInt()
        dropped = inp.readInt()
        finished = inp.readInt()
    }
}

