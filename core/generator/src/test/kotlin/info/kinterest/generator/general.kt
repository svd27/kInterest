package info.kinterest.generator

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import info.kinterest.entity.KIEntityMeta
import info.kinterest.entity.LongPropertyMeta
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isFalse
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.memberProperties

class GeneratorSpek : Spek({
    describe("the generator") {
        describe("working on a simple entity") {
            val kt = SourceFile.kotlin("test.kt", """
            package com.example.test
            import info.kinterest.annotations.*
            import info.kinterest.entity.*
            
            @Entity
            @GuarantueedUnique
            interface Test : KIEntity<Long> {
              var name : String
              var age : Int
              val nValue : Long?
            }
        """.trimIndent())
            it("works without an error") {
                val result = KotlinCompilation().apply {
                    sources = listOf(kt)

                    // pass your own instance of an annotation processor
                    annotationProcessors = listOf(Processor())

                    kaptArgs = mutableMapOf("targets" to "jvm")

                    inheritClassPath = true
                    messageOutputStream = System.out // see diagnostics in real time
                }.compile()


                val kc = result.classLoader.loadClass("com.example.test.jvm.TestJvm").kotlin
                val np = kc.memberProperties.find { it.name == "name" }
                require(np != null)
                assert(np.returnType.classifier == String::class) {
                    "expected ${String::class} but got ${np.returnType} of Type ${np.returnType::class}"
                }

                val meta = kc.companionObjectInstance
                require(meta is KIEntityMeta)
                expectThat(meta.idType).isA<LongPropertyMeta>()
                expectThat(meta.idGenerated).isFalse()

                assert(result.exitCode == KotlinCompilation.ExitCode.OK)
                assert(result.messages.contains("!!!processor!!!"))
            }
        }
        describe("working on a class hierarchy") {
            val kt = SourceFile.kotlin("person.kt", """
            package com.example.test
            import info.kinterest.annotations.*
            import info.kinterest.entity.*
            
            interface MarkerParent
            
            interface Marker : MarkerParent
            
            @Entity
            interface Person : KIEntity<Long> {
              var name : String
              var age : Int
            }
            
            @Entity
            interface Employee : Person {
              val salary : Int
            }
            
            @Entity
            interface Manager : Employee, Marker {
              val department : String
            }
        """.trimIndent())

            val result = KotlinCompilation().apply {
                sources = listOf(kt)

                // pass your own instance of an annotation processor
                annotationProcessors = listOf(Processor())

                kaptArgs = mutableMapOf("targets" to "jvm")

                inheritClassPath = true
                messageOutputStream = System.out // see diagnostics in real time
            }.compile()

            it("works without an error") {
                assert(result.exitCode == KotlinCompilation.ExitCode.OK)
            }

            it("manger has the proper supertypes") {
                val managerKlass = result.classLoader.loadClass("com.example.test.jvm.ManagerJvm").kotlin
                assert(
                        managerKlass.supertypes.find {
                            val cl = it.classifier
                            cl is KClass<*> && cl.simpleName == "EmployeeJvm"
                        } != null
                )
                val meta = managerKlass.companionObjectInstance
                require(meta is KIEntityMeta)
                assert(meta.idGenerated==true)
            }
        }
    }
})