package info.kinterest.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import info.kinterest.DONTDOTHIS
import info.kinterest.annotations.GuarantueedUnique
import info.kinterest.datastore.Datastore
import info.kinterest.entity.*
import mu.KotlinLogging
import org.jetbrains.annotations.Nullable
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import kotlin.reflect.KClass

abstract class Generator {
    abstract fun generate(type: TypeElement, processingEnvironment: ProcessingEnvironment, roundEnvironment: RoundEnvironment): FileSpec?
}

val log = KotlinLogging.logger { }

operator fun TypeElement.contains(annotation: KClass<*>) = annotation.qualifiedName in annotationMirrors.map { it.annotationType.toString() }
operator fun ExecutableElement.contains(annotation: KClass<*>) = annotation.qualifiedName in annotationMirrors.map { it.annotationType.toString() }

val KClass<*>.asPropertyMeta : KClass<*>
  get() = when(this) {
      Long::class -> LongPropertyMeta::class
      Int::class -> IntPropertyMeta::class
      String::class -> StringPropertyMeta::class
      Double::class -> DoublePropertyMeta::class
      else -> ReferenceProperty::class
  }

class JvmGenerator : Generator() {
    data class Hierarchy(val parent: DeclaredType?, val base: DeclaredType, val idType: TypeMirror) {
        val parentAsType: TypeElement?
            get() = parent?.asElement() as? TypeElement
        val baseAsType: TypeElement
            get() = base.asElement() as? TypeElement ?: DONTDOTHIS()
    }

    override fun generate(type: TypeElement, processingEnvironment: ProcessingEnvironment, roundEnvironment: RoundEnvironment): FileSpec? {
        fun note(msg: String) = processingEnvironment.note(msg)
        val props = type.enclosedElements.filterIsInstance<ExecutableElement>().filter { it.simpleName.toString().run { startsWith("get") && length > 3 && it.parameters.isEmpty() } }.map { PropertyDescriptor(it, type) }
        props.forEach { note("$it") }

        fun findParentBaseAndIdType(type: DeclaredType): Hierarchy? = run {
            val tel = type.asElement() as TypeElement
            val hierarchies = tel.interfaces.filterIsInstance<DeclaredType>().map { it to findParentBaseAndIdType(it) }.map { it.second?.copy(parent = it.first) }.filterNotNull()
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
                    Hierarchy(null, type, idKlass)
                }.firstOrNull()
            } else {
                require(hierarchies.size == 1)
                hierarchies[0]
            }
        }

        val hierarchy = findParentBaseAndIdType(type.asType() as DeclaredType)
        require(hierarchy != null) {
            "hierarchy is null"
        }
        processingEnvironment.note("${type.simpleName}: hierarchy $hierarchy")

        val idKlass = hierarchy.idType.asKClass()
        val idGenerated = GuarantueedUnique::class !in type

        val targetName = type.simpleName.toString() + "Jvm"
        val targetPackage = type.qualifiedName.split('.').dropLast(1).joinToString(".") + ".jvm"

        //leave in as a comment coz its a usefull pattern
        //val dont = MemberName("info.kinterest", "DONTDOTHIS")

        val targetClass = ClassName(targetPackage, targetName)
        val base = TypeSpec.classBuilder(targetClass).addSuperinterface(type.asType().asTypeName()).addModifiers(KModifier.OPEN)

        if (hierarchy.parent != null) {
            val typeElement = hierarchy.parentAsType!!
            val pn = ClassName(typeElement.qualifiedName.split(".").dropLast(1).plus("jvm").joinToString("."), typeElement.simpleName.toString() + "Jvm")
            base.primaryConstructor(FunSpec.constructorBuilder().addParameter("_store", Datastore::class).addParameter("id", idKlass).build())
            base.superclass(pn).addSuperclassConstructorParameter("_store").addSuperclassConstructorParameter("id")
        } else {
            base.addSuperinterface(KIEntityJvm::class.parameterizedBy(idKlass))
            base.primaryConstructor(FunSpec.constructorBuilder().addParameter("_store", Datastore::class, KModifier.OVERRIDE).addParameter("id", idKlass, KModifier.OVERRIDE).build())
            base.addProperty(PropertySpec.builder("_store", Datastore::class).initializer("_store").build()).addProperty(PropertySpec.builder("id", idKlass).initializer("id").build())
        }

        base.addProperty(PropertySpec.builder("_meta", KIEntityMeta::class, KModifier.OVERRIDE).initializer(targetName).build())

        val asTransient = FunSpec.builder("asTransient").addModifiers(KModifier.OVERRIDE).returns(KITransientEntity::class.asClassName().parameterizedBy(idKlass.asClassName())).addCode(
                "return ${type.simpleName}Transient(this)"
        )
        base.addFunction(asTransient.build())
        base.addFunction(FunSpec.builder("equals").addModifiers(KModifier.OVERRIDE).addParameter("other", ANY.copy(true)).returns(BOOLEAN).addCode("return _equals(other)").build())
        base.addFunction(FunSpec.builder("hashCode").addModifiers(KModifier.OVERRIDE).returns(INT).addCode("return _hashCode()").build())
        base.addFunction(FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).returns(STRING).addCode("return _toString()").build())

        props.forEach {
            base.addProperty(createProperty(it))
        }

        val companion = TypeSpec.companionObjectBuilder()
        companion.addSuperinterface(KIEntityMetaJvm::class)
        companion.addProperty(
                PropertySpec.builder("type", KClass::class.asClassName().parameterizedBy(STAR),
                        KModifier.OVERRIDE).initializer("$targetClass::class").build()
        )

        companion.addProperty(
                PropertySpec.builder("baseMeta", KIEntityMeta::class,
                        KModifier.OVERRIDE).initializer("%T", hierarchy.baseAsType.asClassName("jvm", "Jvm")).build()
        )
        val builder = PropertySpec.builder("parentMeta", KIEntityMeta::class.asTypeName().copy(true),
                KModifier.OVERRIDE)
        if (hierarchy.parentAsType != null)
            builder.initializer("%T", hierarchy.parentAsType!!.asClassName("jvm", "Jvm"))
        else builder.initializer("null")
        companion.addProperty(builder.build())

        val idTypeBuilder = PropertySpec.builder("idType", idKlass.asPropertyMeta, KModifier.OVERRIDE)
        if(idKlass.asPropertyMeta == ReferenceProperty::class) {
            idTypeBuilder.initializer("%T(\"id\", %T::class, false, true)", idKlass.asPropertyMeta, idKlass)
        } else {
            idTypeBuilder.initializer("%T(\"id\", false, true)", idKlass.asPropertyMeta)
        }

        companion.addProperty(idTypeBuilder.build())
        companion.addProperty(PropertySpec.builder("idGenerated", Boolean::class, KModifier.OVERRIDE).initializer("$idGenerated").build())


        props.forEach {
            val pspec = PropertySpec.builder(it.upperCase, it.type.asKClass().asPropertyMeta)
                    .mutable(false)
            if(it.type.asKClass().asPropertyMeta == ReferenceProperty::class) {
                pspec.initializer("%T(\"${it.name}\", %T, ${it.nullable}, ${it.readOnly})", it.type.asKClass().asPropertyMeta, it.type.asKClass().asPropertyMeta)
            } else {
                pspec.initializer("%T(\"${it.name}\", ${it.nullable}, ${it.readOnly})", it.type.asKClass().asPropertyMeta)
            }
            companion.addProperty(
                    pspec

                    .build())
        }

        companion.addProperty(
                PropertySpec.builder("properties", Map::class.asClassName().parameterizedBy(String::class.asTypeName(),
                        PropertyMeta::class.asTypeName()), KModifier.OVERRIDE).initializer("mapOf(${
                
                props.map { "\"${it.name}\" to ${it.upperCase}" }.joinToString(",")
                })${
                  if(hierarchy.parent!=null) " + " + hierarchy.parentAsType!!.asClassName("jvm", "Jvm") + ".properties" else ""
                }").build()
        )
        companion.addFunction(
                FunSpec.builder("instance").addTypeVariable(TypeVariableName("ID", bounds = listOf(Any::class))).addModifiers(KModifier.OVERRIDE).addParameter("_store", Datastore::class).addParameter("id", Any::class).addCode("return $targetName(_store, id as %T) as KIEntity<ID>", idKlass.asTypeName()).returns(KIEntity::class.asClassName().parameterizedBy(TypeVariableName.invoke("ID"))).build())

        companion.addFunction(
                FunSpec.builder("equals").returns(Boolean::class).addModifiers(KModifier.OVERRIDE).addParameter("other", ANY.copy(true)).addCode("return _equals(other)").build()
        )

        companion.addFunction(
                FunSpec.builder("hashCode").returns(Int::class).addModifiers(KModifier.OVERRIDE).addCode("return _hashCode()").build()
        )

        companion.addFunction(
                FunSpec.builder("toString").returns(String::class).addModifiers(KModifier.OVERRIDE).addCode("return _toString()").build()
        )

        base.addType(companion.build())

        val mutableMap = ClassName("kotlin.collections", "MutableMap")
        val transient = createTransient(type, hierarchy, idKlass, mutableMap, props, targetName, targetClass)

        return FileSpec.builder(targetPackage, targetName).addType(base.build()).addType(transient.build()).build()
    }

    private fun createTransient(type: TypeElement, hierarchy: Hierarchy, idKlass: KClass<*>, mutableMap: ClassName, props: List<PropertyDescriptor>, targetName: String, targetClass: ClassName): TypeSpec.Builder {
        val transient = TypeSpec.classBuilder("${type.simpleName}Transient").addModifiers(KModifier.OPEN)

        if (hierarchy.parentAsType != null) {
            val pt = hierarchy.parentAsType
            val cn = pt!!.asClassName("jvm", "Transient")
            transient.superclass(cn)
            transient.addSuperclassConstructorParameter("properties")
            transient.primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec("properties", mutableMap.parameterizedBy(String::class.asTypeName(),
                            Any::class.asTypeName().copy(true))))
                    .build())
        } else {
            transient.addSuperinterface(KITransientEntity::class.asClassName().parameterizedBy(idKlass.asTypeName()))
            transient.primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec("properties", mutableMap.parameterizedBy(String::class.asTypeName(), Any::class.asTypeName()
                            .copy(true)), KModifier.OVERRIDE)).build())
            transient.addProperty(PropertySpec.builder("properties", mutableMap.parameterizedBy(String::class.asTypeName(), Any::class.asTypeName().copy(true))).initializer("properties").build())
        }

        transient.addSuperinterface(type.asClassName())


        props.forEach {
            transient.addProperty(PropertySpec.builder(it.name, it.type.asKClass().asTypeName().copy(it.nullable), KModifier.OVERRIDE).mutable(!it.readOnly).delegate("properties").build())
        }

        transient.addProperty(PropertySpec.builder("_meta", KIEntityMeta::class, KModifier.OVERRIDE).initializer(targetName).build())
        val nostore = MemberName("info.kinterest.datastore", "NOSTORE")
        transient.addProperty(PropertySpec.builder("_store", Datastore::class, KModifier.OVERRIDE).initializer("%M", nostore).build())
        val propsCode = "mutableMapOf<String, Any?>(${props.joinToString(",") { CodeBlock.builder().add("%S to entity.${it.name}", it.name).build().toString() }})"

        transient.addFunction(FunSpec.constructorBuilder().addParameter("entity", targetClass).callThisConstructor(propsCode).build())
        fun createPropsPatameters(funSpec: FunSpec.Builder) {
            props.forEach {
                funSpec.addParameter(it.name, it.type.asKClass().asTypeName().copy(it.nullable))
            }
            funSpec.addParameter(ParameterSpec.builder("id", idKlass.asTypeName().copy(true)).defaultValue("null").build())
            val ctorProps = "mutableMapOf<String, Any?>(${props.joinToString(",") { 
                CodeBlock.builder().add("%S to ${it.name}", it.name).build().toString() }
            }, \"id\" to id)"
            funSpec.callThisConstructor(ctorProps)
        }

        val ctorBuilder = FunSpec.constructorBuilder()
        createPropsPatameters(ctorBuilder)
        transient.addFunction(ctorBuilder.build())

        transient.addFunction(FunSpec.builder("asTransient").addModifiers(KModifier.OVERRIDE)
                .addStatement("return ${type.simpleName}Transient(properties)").build())

        /*if (hierarchy.parent == null) {
            transient.addFunction(FunSpec.builder("getValue").addModifiers(KModifier.OVERRIDE)
                    .addTypeVariable(TypeVariableName("V")).addParameter("meta", PropertyMeta::class)
                    .returns(TypeVariableName("V").copy(true))
                    .addCode("return properties[meta.name] as V?").build())
            transient.addFunction(FunSpec.builder("setValue").addModifiers(KModifier.OVERRIDE)
                    .addTypeVariable(TypeVariableName("V"))
                    .addParameter("meta", PropertyMeta::class)
                    .addParameter("v", TypeVariableName("V").copy(true))
                    .addCode("properties[meta.name] = v").build())
        }*/

        return transient
    }

    private fun createProperty(it: PropertyDescriptor) = run {
        val ps = PropertySpec.builder(it.name, it.type.asKClass().asTypeName().copy(it.nullable), KModifier.OVERRIDE).mutable(!it.readOnly)
                .getter(FunSpec.getterBuilder().addStatement("""return getValue<%T>(${it.upperCase}) as %T${if(it.nullable) "?" else ""}""", it.type.asKClass(), it.type.asKClass()).build())
        if (!it.readOnly) ps.setter(FunSpec.setterBuilder().addParameter("v", it.type.asKClass()).addStatement("""setValue(${it.upperCase}, v)""", String::class).build())
        ps.build()
    }
}

fun TypeElement.asClassName(packageSuffix: String, nameSuffix: String) = ClassName(qualifiedName.split(".").dropLast(1).joinToString(".") + ".$packageSuffix", simpleName.toString() + nameSuffix)

fun TypeMirror.asKClass() = asTypeName().toString().run {
    when (this) {
        "kotlin.Long" -> Long::class
        "kotlin.Int" -> Int::class
        "java.lang.String" -> String::class
        else -> Class.forName(this).kotlin
    }
}

class PropertyDescriptor(getter: ExecutableElement, enclosedBy: TypeElement) {
    val name: String = run {
        val pname = getter.simpleName.toString().substring(3)
        pname.replaceRange(0, 1, "${pname.get(0).toLowerCase()}")
    }

    val nullable: Boolean = Nullable::class in getter
    val readOnly : Boolean = enclosedBy.enclosedElements.filterIsInstance<ExecutableElement>().filter {
        it.simpleName.startsWith("s" + getter.simpleName.substring(1))
    }.isEmpty()
    val type: TypeMirror = getter.returnType

    val upperCase : String get() = name.map { if(it.isUpperCase()) "_$it" else "$it" }.joinToString("").toUpperCase()

    override fun toString(): String = "property: $name ro: $readOnly type: $type"
}

