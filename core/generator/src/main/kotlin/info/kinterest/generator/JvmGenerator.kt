package info.kinterest.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.metadata.*
import info.kinterest.DONTDOTHIS
import info.kinterest.annotations.GuarantueedUnique
import info.kinterest.datastore.Datastore
import info.kinterest.entity.KIEntityJvm
import info.kinterest.entity.KIEntityMeta
import info.kinterest.entity.KITransientEntity
import kotlinx.metadata.KmClassifier
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import kotlin.reflect.KClass

operator fun ExecutableElement.contains(annotation: KClass<*>) = annotation.qualifiedName in annotationMirrors.map { it.annotationType.toString() }

@KotlinPoetMetadataPreview
class JvmGenerator : Generator() {
    data class Hierarchy(val parent: DeclaredType?, val base: DeclaredType, val idType: TypeMirror, val path: List<Hierarchy>) {
        val parentAsType: TypeElement?
            get() = parent?.asElement() as? TypeElement
        val baseAsType: TypeElement
            get() = base.asElement() as? TypeElement ?: DONTDOTHIS()
    }

    fun extractProperties(type: TypeElement, metadata: ImmutableKmClass) : List<Property> = metadata.properties.map {
        //val getterName = "get${it.name[0]?.toUpperCase()}${it.name.substring(1)}"
        //val getter = type.enclosedElements.filterIsInstance<ExecutableElement>().filter { it.simpleName.toString() == getterName }.first()
        Property(it)
    }

    override fun generate(type: TypeElement, processingEnvironment: ProcessingEnvironment, roundEnvironment: RoundEnvironment): FileSpec? = run {
        fun note(msg: String) = processingEnvironment.note(msg)

        fun findParentBaseAndIdType(type: DeclaredType): JvmGeneratorOld.Hierarchy? = run {
            val tel = type.asElement() as TypeElement
            val hierarchies = tel.interfaces.filterIsInstance<DeclaredType>()
                    .map { it to findParentBaseAndIdType(it) }
                    .map {
                        val copy = it.second?.copy(parent = it.first)
                        copy?.copy(path = copy.path + copy)
                    }.filterNotNull()
            if (hierarchies.isEmpty()) {
                tel.interfaces.filterIsInstance<DeclaredType>().map {
                    processingEnvironment.note("DeclaredType ${it}")
                    println("DeclaredType ${it}")
                    it to it.asElement()
                }.filterIsInstance<Pair<DeclaredType, TypeElement>>().filter {
                    processingEnvironment.note("Pair ${it.second}")
                    it.second.qualifiedName.contentEquals("info.kinterest.entity.KIEntity")
                }.map {
                    require(it.first.typeArguments.size == 1)
                    val idKlass = it.first.typeArguments[0]
                    JvmGeneratorOld.Hierarchy(null, type, idKlass, listOf())
                }.firstOrNull()
            } else {
                require(hierarchies.size == 1)
                hierarchies[0]
            }
        }

        val meta = type.toImmutableKmClass()
        val props = extractProperties(type, meta)
        val hierarchy = findParentBaseAndIdType(type.asType() as DeclaredType)
        require(hierarchy != null) {
            "hierarchy is null"
        }
        processingEnvironment.note("${type.simpleName}: hierarchy $hierarchy")
        val allProperties = props + hierarchy.path
                .flatMap { (it.parent?.asElement() as? TypeElement)?.let { extractProperties(it, it.toImmutableKmClass()) } ?: listOf() }

        val idGenerated = GuarantueedUnique::class !in type

        val targetName = type.simpleName.toString() + "Jvm"
        val targetPackage = type.qualifiedName.split('.').dropLast(1).joinToString(".") + ".jvm"

        val targetClass = ClassName(targetPackage, targetName)
        val base = TypeSpec.classBuilder(targetClass).addSuperinterface(type.asType().asTypeName()).addModifiers(KModifier.OPEN)
        val baseTypeMeta = hierarchy.baseAsType.toImmutableKmClass()
        val kiEntity = baseTypeMeta.supertypes.find {
            val classifier = it.classifier
            classifier is KmClassifier.Class && "info/kinterest/entity/KIEntity" == classifier.name
        }
        val idClass = kiEntity?.arguments?.firstOrNull()?.type?.classifier as? KmClassifier.Class
        val idFQN = idClass?.name?.replace("/", ".")?:throw IllegalStateException("no id class found")
        val idCn = ClassName(idFQN.split(".").dropLast(1).joinToString("."), idFQN.split(".").last())

        if (hierarchy.parent != null) {
            val typeElement = hierarchy.parentAsType!!
            val pn = ClassName(typeElement.qualifiedName.split(".").dropLast(1).plus("jvm")
                    .joinToString("."), typeElement.simpleName.toString() + "Jvm")
            base.primaryConstructor(FunSpec.constructorBuilder().addParameter("_store", Datastore::class).addParameter("id", idCn).build())
            base.superclass(pn).addSuperclassConstructorParameter("_store").addSuperclassConstructorParameter("id")
        } else {
            base.addSuperinterface(KIEntityJvm::class.asClassName().parameterizedBy(idCn))
            base.primaryConstructor(FunSpec.constructorBuilder().addParameter("_store", Datastore::class, KModifier.OVERRIDE).addParameter("id", idCn, KModifier.OVERRIDE).build())
            base.addProperty(PropertySpec.builder("_store", Datastore::class).initializer("_store").build()).addProperty(PropertySpec.builder("id", idCn).initializer("id").build())
        }

        base.addProperty(PropertySpec.builder("_meta", KIEntityMeta::class, KModifier.OVERRIDE).initializer(targetName).build())

        val asTransient = FunSpec.builder("asTransient").addModifiers(KModifier.OVERRIDE).returns(KITransientEntity::class.asClassName()
                .parameterizedBy(idCn)
                )
                .addCode(
                "return ${type.simpleName}Transient(this)"
        )
        base.addFunction(asTransient.build())
        base.addFunction(FunSpec.builder("equals").addModifiers(KModifier.OVERRIDE).addParameter("other", ANY.copy(true)).returns(BOOLEAN).addCode("return _equals(other)").build())
        base.addFunction(FunSpec.builder("hashCode").addModifiers(KModifier.OVERRIDE).returns(INT).addCode("return _hashCode()").build())
        base.addFunction(FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).returns(STRING).addCode("return _toString()").build())

        props.forEach {
            //base.addProperty(createProperty(it))
        }

        val transient : TypeSpec.Builder

        //FileSpec.builder(targetPackage, targetName).addType(base.build()).addType(transient.build()).build()
        null
    }
}

@KotlinPoetMetadataPreview
class Property(val p:ImmutableKmProperty) {
    val name = p.name
    val nullable = p.returnType.isNullable
    val mutable = p.isVar
    val typeMeta = p.returnType
    val isRaw = p.returnType.arguments.isEmpty()
}