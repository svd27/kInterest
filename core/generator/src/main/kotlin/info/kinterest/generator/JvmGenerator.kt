package info.kinterest.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.metadata.*
import info.kinterest.DONTDOTHIS
import info.kinterest.annotations.GuarantueedUnique
import info.kinterest.datastore.Datastore
import info.kinterest.entity.*
import info.kinterest.jvm.backend.entity.*
import kotlinx.metadata.KmClassifier
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import kotlin.reflect.KClass


operator fun TypeElement.contains(annotation: KClass<*>) = annotation.qualifiedName in annotationMirrors.map { it.annotationType.toString() }
fun TypeElement.asClassName(packageSuffix: String, nameSuffix: String) = ClassName(qualifiedName.split(".").dropLast(1).joinToString(".") + ".$packageSuffix", simpleName.toString() + nameSuffix)

@KotlinPoetMetadataPreview
val ImmutableKmType.propertyMeta: KClass<*>
    get() = when {
        this.arguments.isEmpty() -> {
            when (val classifier1 = this.classifier) {
                is KmClassifier.Class ->
                    when (classifier1.name) {
                        "kotlin/Long" -> LongPropertyMeta::class
                        "kotlin/Int" -> IntPropertyMeta::class
                        "kotlin/String" -> StringPropertyMeta::class
                        else -> ReferenceProperty::class
                    }
                else -> throw IllegalStateException("bad classifier in $this $classifier1")
            }
        }
        else -> throw IllegalStateException("dont know how to handle $this")
    }

@KotlinPoetMetadataPreview
class JvmGenerator : Generator() {
    data class Hierarchy(val parent: DeclaredType?, val base: DeclaredType, val idType: TypeMirror, val path: List<Hierarchy>) {
        val parentAsType: TypeElement?
            get() = parent?.asElement() as? TypeElement
        val baseAsType: TypeElement
            get() = base.asElement() as? TypeElement ?: DONTDOTHIS()
    }

    private fun extractProperties(type: TypeElement, metadata: ImmutableKmClass): List<Property> = metadata.properties.map { kmProperty ->
        val getterName = "get${kmProperty.name[0].toUpperCase()}${kmProperty.name.substring(1)}"
        val getter = type.enclosedElements.filterIsInstance<ExecutableElement>().first { it.simpleName.toString() == getterName }
        Property(kmProperty, getter.returnType)
    }

    override fun generate(type: TypeElement, processingEnvironment: ProcessingEnvironment, roundEnvironment: RoundEnvironment): FileSpec? = run {
        fun findParentBaseAndIdType(type: DeclaredType): Hierarchy? = run {
            val tel = type.asElement() as TypeElement
            val hierarchies = tel.interfaces.filterIsInstance<DeclaredType>()
                    .map { it to findParentBaseAndIdType(it) }.mapNotNull {
                        val copy = it.second?.copy(parent = it.first)
                        copy?.copy(path = copy.path + copy)
                    }
            if (hierarchies.isEmpty()) {
                tel.interfaces.asSequence().filterIsInstance<DeclaredType>().map {
                    processingEnvironment.note("DeclaredType $it")
                    it to it.asElement()
                }.filterIsInstance<Pair<DeclaredType, TypeElement>>().filter {
                    processingEnvironment.note("Pair ${it.second}")
                    it.second.qualifiedName.contentEquals("info.kinterest.entity.KIEntity")
                }.map {
                    require(it.first.typeArguments.size == 1)
                    val idKlass = it.first.typeArguments[0]
                    Hierarchy(null, type, idKlass, listOf())
                }.firstOrNull()
            } else {
                require(hierarchies.size == 1)
                hierarchies[0]
            }
        }

        val meta = type.toImmutableKmClass()
        val props = extractProperties(type, meta)
        val metaProps = props.map { PropertyMetaDescriptor(it) }
        val hierarchy = findParentBaseAndIdType(type.asType() as DeclaredType)
        require(hierarchy != null) {
            "hierarchy is null"
        }
        processingEnvironment.note("${type.simpleName}: hierarchy $hierarchy")
        val allProperties = props + hierarchy.path
                .flatMap {
                    (it.parent?.asElement() as? TypeElement)?.let { tel -> extractProperties(tel, tel.toImmutableKmClass()) }
                            ?: listOf()
                }

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
        val idType = kiEntity?.arguments?.firstOrNull()?.type
        val idClass = idType?.classifier as? KmClassifier.Class
        val idFQN = idClass?.name?.replace("/", ".") ?: throw IllegalStateException("no id class found")
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

        metaProps.forEach {
            base.addProperty(it.propertySpec)
        }

        val companion = TypeSpec.companionObjectBuilder()
        companion.addSuperinterface(KIEntityMetaJvm::class)
        companion.addProperty(
                PropertySpec.builder("type", KClass::class.asClassName().parameterizedBy(STAR),
                        KModifier.OVERRIDE).initializer("%T::class", type.asClassName()).build()
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

        val idMeta = idType.propertyMeta
        val idTypeBuilder = PropertySpec.builder("idType", idMeta, KModifier.OVERRIDE)
        if (idMeta == ReferenceProperty::class) {
            idTypeBuilder.initializer("%T(\"id\", %T::class, false, true)", idMeta, idCn)
        } else {
            idTypeBuilder.initializer("%T(\"id\", false, true)", idMeta)
        }

        companion.addProperty(idTypeBuilder.build())
        companion.addProperty(PropertySpec.builder("idGenerated", Boolean::class, KModifier.OVERRIDE).initializer("$idGenerated").build())

        metaProps.forEach {
            companion.addProperty(it.metaPropertySpec)
        }

        companion.addProperty(
                PropertySpec.builder("properties", Map::class.asClassName().parameterizedBy(String::class.asTypeName(),
                        PropertyMeta::class.asTypeName()), KModifier.OVERRIDE).initializer("mapOf(${

                props.joinToString(",") { "\"${it.name}\" to ${it.metaName}" }
                })${
                if (hierarchy.parent != null) " + " + hierarchy.parentAsType!!.asClassName("jvm", "Jvm") + ".properties" else ""
                }").build()
        )
        companion.addFunction(
                FunSpec.builder("instance").addTypeVariable(TypeVariableName("ID", bounds = listOf(Any::class)))
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("_store", Datastore::class)
                        .addParameter("id", Any::class).addCode("return $targetName(_store, id as %T) as KIEntity<ID>", idCn).returns(KIEntity::class.asClassName().parameterizedBy(TypeVariableName.invoke("ID"))).build())

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

        val transient: TypeSpec.Builder = TypeSpec.classBuilder(type.simpleName.toString()+"Transient").addModifiers(KModifier.OPEN)

        if (hierarchy.parentAsType != null) {
            val pt = hierarchy.parentAsType
            val cn = pt!!.asClassName("jvm", "Transient")
            transient.superclass(cn)
            transient.addSuperclassConstructorParameter("properties")
            transient.primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec("properties", MUTABLE_MAP.parameterizedBy(String::class.asTypeName(),
                            ANY.copy(true))))
                    .build())
        } else {
            transient.addSuperinterface(KITransientEntity::class.asClassName().parameterizedBy(idCn))
            transient.primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec("properties", MUTABLE_MAP.parameterizedBy(String::class.asTypeName(), ANY
                            .copy(true)), KModifier.OVERRIDE)).build())
            transient.addProperty(PropertySpec.builder("properties", MUTABLE_MAP.parameterizedBy(STRING, ANY.copy(true))).initializer("properties").build())
        }

        transient.addSuperinterface(type.asClassName())

        metaProps.forEach {
            transient.addProperty(PropertySpec.builder(it.p.name, it.className.copy(it.p.nullable), KModifier.OVERRIDE).mutable(it.p.mutable).delegate("properties").build())
        }

        transient.addProperty(PropertySpec.builder("_meta", KIEntityMeta::class, KModifier.OVERRIDE).initializer(targetName).build())
        val nostore = MemberName("info.kinterest.datastore", "NOSTORE")
        transient.addProperty(PropertySpec.builder("_store", Datastore::class, KModifier.OVERRIDE).initializer("%M", nostore).build())
        val propsCode = "mutableMapOf<String, Any?>(${allProperties.joinToString(",") { 
            CodeBlock.builder().add("%S to entity.${it.name}", it.name).build().toString() }
        })"

        transient.addFunction(FunSpec.constructorBuilder().addParameter("entity", targetClass).callThisConstructor(propsCode).build())

        fun createPropsParameters(funSpec: FunSpec.Builder) {
            val allPropertiesMeta = allProperties.map { PropertyMetaDescriptor(it) }
            allPropertiesMeta.forEach {
                funSpec.addParameter(it.p.name, it.realClassName.copy(it.p.nullable))
            }
            funSpec.addParameter(ParameterSpec.builder("id", idCn.copy(true)).defaultValue("null").build())
            val ctorProps = "mutableMapOf<String, Any?>(${allProperties.joinToString(",") {
                CodeBlock.builder().add("%S to ${it.name}", it.name).build().toString()
            }
            }, \"id\" to id)"
            funSpec.callThisConstructor(ctorProps)
        }

        val ctorBuilder = FunSpec.constructorBuilder()
        createPropsParameters(ctorBuilder)
        transient.addFunction(ctorBuilder.build())

        transient.addFunction(FunSpec.builder("asTransient").addModifiers(KModifier.OVERRIDE)
                .addStatement("return ${type.simpleName}Transient(properties)").build())

        FileSpec.builder(targetPackage, targetName).addType(base.build())
                .addType(transient.build())
                .build()
    }
}

@KotlinPoetMetadataPreview
class Property(p: ImmutableKmProperty, val typeElement: TypeMirror) {
    val name = p.name
    val nullable = p.returnType.isNullable
    val mutable = p.isVar
    val readOnly = p.isVal
    val type = p.returnType
    val metaName = name.map { if (it.isUpperCase()) "_$it" else "$it" }.joinToString("").toUpperCase()
    val className = (p.returnType.classifier as? KmClassifier.Class)?.name
            ?.let {
                ClassName(it.split("/").dropLast(1).joinToString("."),
                        it.split("/").last())
            } ?: throw IllegalStateException("bad classifier in ${p.returnType}")
}

@KotlinPoetMetadataPreview
class PropertyMetaDescriptor(val p: Property) {
    private val classifier = p.type.classifier as? KmClassifier.Class ?: throw IllegalArgumentException()
    private val metaClass: KClass<*>
    private val collectionClassName: ClassName?
    private val containedInCollection: ClassName?
    private val mutableCollection: Boolean?
    private val references: ClassName?
    val realClassName : TypeName
        get() = collectionClassName?.parameterizedBy(containedInCollection!!) ?: p.className

    init {
        var containedInCollection: ClassName? = null
        var mutableCollection = false
        var references: ClassName? = null
        var collectionClassName: ClassName? = null
        metaClass = if (p.type.arguments.isEmpty()) {
            references = classifier.typeName
            when (classifier.name) {
                "kotlin/Long" -> LongPropertyMeta::class
                "kotlin/String" -> StringPropertyMeta::class
                "kotlin/Int" -> IntPropertyMeta::class
                else -> {
                    val typeElement = (p.typeElement as? DeclaredType)?.asElement() as? TypeElement
                    require(typeElement != null)
                    references = typeElement.asClassName()
                    if (typeElement.containsSuper(KIEntity::class)) {
                        SingleRelationProperty::class
                    } else {
                        ReferenceProperty::class
                    }
                }
            }
        } else {
            if (classifier.name.startsWith("kotlin/collections")) {
                check(!(p.mutable || p.nullable))
                mutableCollection = "Mutable" in classifier.name
                if (p.type.arguments.size == 1) {
                    val arg = p.type.arguments.first()
                    val contained = arg.type?.classifier as? KmClassifier.Class ?: throw IllegalStateException()
                    containedInCollection = contained.typeName
                    collectionClassName = classifier.typeName
                    when {
                        "Set" in classifier.name -> SetRelationProperty::class
                        "List" in classifier.name -> ListRelationProperty::class
                        else -> throw IllegalArgumentException()
                    }
                } else throw IllegalArgumentException()
            } else throw IllegalArgumentException()
        }

        this.containedInCollection = containedInCollection
        this.collectionClassName = collectionClassName
        this.references = references
        this.mutableCollection = mutableCollection
    }

    val className = (collectionClassName?.parameterizedBy(containedInCollection!!) ?: references!!).copy(p.nullable)
    val propertySpec: PropertySpec = run {
        val propertySpec = PropertySpec.builder(p.name, className, KModifier.OVERRIDE)
        if (collectionClassName != null) {
            val delegate = if (mutableCollection == true) {
                if ("List" in collectionClassName.simpleName) MutableRelationListDelegate::class
                else MutableRelationSetDelegate::class
            } else if ("List" in collectionClassName.simpleName) RelationListDelegate::class
            else RelationSetDelegate::class
            propertySpec.delegate("%T(this, ${p.metaName})", delegate.asClassName().parameterizedBy(containedInCollection!!).copy(nullable = p.nullable))
        } else {
            if(metaClass == SingleRelationProperty::class) {
                propertySpec.delegate("%T(this, ${p.metaName})", SingleRelationDelegate::class.asClassName().parameterizedBy(p.className.copy(nullable = p.nullable)))
            } else {
                propertySpec.getter(FunSpec.getterBuilder().addStatement("return getValue<%T>(${p.metaName})", p.className).build())
                if (p.mutable)
                    propertySpec.setter(FunSpec.setterBuilder().addParameter("v", p.className).addStatement("setValue(${p.metaName}, v)").build())
            }
        }
        propertySpec.mutable(p.mutable).build()
    }

    private fun metaToInitialiser(): CodeBlock = when (metaClass) {
        IntPropertyMeta::class, LongPropertyMeta::class, StringPropertyMeta::class ->
            CodeBlock.of("%T(%S, ${p.nullable}, ${p.readOnly})", metaClass, p.name)
        ReferenceProperty::class ->
            CodeBlock.of("%T(%S, %T::class, ${p.nullable}, ${p.readOnly})", metaClass, p.name, references)
        SingleRelationProperty::class ->
            CodeBlock.of("%T(%S, %T, ${p.nullable}, ${p.readOnly})", metaClass, p.name, references?.jvmClassName)
        SetRelationProperty::class, ListRelationProperty::class ->
            CodeBlock.of("%T(%S, ${p.nullable}, ${p.readOnly}, ${mutableCollection}, %T)", metaClass, p.name, containedInCollection?.jvmClassName)
        else -> throw IllegalStateException("$metaClass")
    }

    val metaPropertySpec: PropertySpec = PropertySpec.builder(p.metaName, metaClass).initializer(metaToInitialiser()).build()
}

fun TypeElement.containsSuper(kClass: KClass<*>): Boolean =
        this.interfaces.filterIsInstance<DeclaredType>().map { it.asElement() }
                .filterIsInstance<TypeElement>().any { it.qualifiedName.toString() == kClass.qualifiedName }


operator fun ExecutableElement.contains(annotation: KClass<*>) = annotation.qualifiedName in annotationMirrors.map { it.annotationType.toString() }

val KmClassifier.Class.typeName: ClassName get() = ClassName(name.split("/").dropLast(1).joinToString("."), name.split("/").last())

val ClassName.jvmClassName: ClassName get() = ClassName("$packageName.jvm", "${simpleName}Jvm")
