package com.space.compiler.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.space.annotations.DTO
import com.space.annotations.DomainModel
import com.space.annotations.MapWith
import com.space.compiler.extensions.*
import com.space.compiler.extensions.capitalize
import com.space.compiler.extensions.decapitalize
import com.space.compiler.extensions.isClass
import com.space.compiler.extensions.isEnum
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class MapperSymbolProcessor(private val logger: KSPLogger, private val codeGenerator: CodeGenerator) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        for (file in resolver.getNewFiles()) {
            val dtoDeclarations = file.filterAnnotatedWith(DTO::class)
            val domainDeclarations = file.filterAnnotatedWith(DomainModel::class)

            val declarations = dtoDeclarations.mapNotNull { dtoDeclaration ->
                val dtoDeclarationName = dtoDeclaration.withoutSuffix(DTO_SUFFIX)
                val domainDeclaration =
                    domainDeclarations.find { it.withoutSuffix(DOMAIN_SUFFIX) == dtoDeclarationName }
                if (domainDeclaration != null) dtoDeclaration to domainDeclaration
                else null
            }

            declarations.forEach { declaration ->
                val dtoDeclaration = declaration.first
                val domainDeclaration = declaration.second
                val fileName: String = dtoDeclaration.withoutSuffix(DTO_SUFFIX)
                val filePackage: String = declaration.first.packageName.asString() + MAPPER_PACKAGE_NAME

                FileSpec.builder(filePackage, "${fileName}Mapper_Gen")
                    .apply {
                        val declarationImpl = "${fileName}Mapper"

                        addType(
                            TypeSpec.classBuilder(declarationImpl)
                                .addOriginatingKSFile(file)
                                .apply {
                                    val dtoDeclarationObject = DeclarationObject(dtoDeclaration, DTO_MODEL, DTO_SUFFIX)
                                    val domainDeclarationObject =
                                        DeclarationObject(domainDeclaration, DOMAIN_MODEL, DOMAIN_SUFFIX)
                                    if (dtoDeclaration.classKind == ClassKind.CLASS) {
                                        generateMapperFunction(dtoDeclarationObject, domainDeclarationObject)
                                        generateMapperFunction(domainDeclarationObject, dtoDeclarationObject)
                                    } else {
                                        generateEnumMapperFunction(dtoDeclarationObject, domainDeclarationObject)
                                        generateEnumMapperFunction(domainDeclarationObject, dtoDeclarationObject)
                                    }
                                }
                                .build())
                    }
                    .build()
                    .writeTo(codeGenerator, aggregating = false)
            }
        }

        return emptyList()
    }

    private fun TypeSpec.Builder.generateMapperFunction(from: DeclarationObject, to: DeclarationObject) {
        val functionName = getFunctionName(from, to)
        val funSpec = FunSpec.builder(functionName)
            .addParameter(from.name, from.declaration.toClassName())
            .returns(to.declaration.toClassName())
        funSpec.addCode(
            CodeBlock.builder()
                .add("return·%T(\n", to.declaration.toClassName()).withIndent {
                    for (param in to.declaration.primaryConstructor!!.parameters) {
                        val paramName = param.name!!.asString()
                        param.getExternalMapper()?.let {
                            val parameterName = it.toClassName().simpleName.removeSuffix(to.suffix)
                            funSpec.addParameter(
                                parameterName.decapitalize(),
                                ClassName("com.space.test.mappers", "${parameterName}Mapper")
                            )
                            add("%1L = ${parameterName.decapitalize()}.${functionName}(${from.name}.%1L),\n", paramName)
                        } ?: run {
                            val fromParam =
                                from.declaration.primaryConstructor!!.parameters.firstOrNull { it.name?.asString() == paramName }
                            if (fromParam != null && fromParam.type.resolve() == param.type.resolve())
                                add("%1L = ${from.name}.%1L,\n", paramName)
                            else {
                                val receiverType = fromParam?.type?.resolve()?.toTypeName()!!
                                val returnType = param.type.resolve().toTypeName()

                                // Create a function with a lambda type
                                val functionType = LambdaTypeName.get(
                                    parameters = arrayOf(receiverType),
                                    returnType = returnType
                                )
                                val spec = ParameterSpec.builder("get${paramName.capitalize()}", functionType).build()
                                funSpec.addParameter(spec)
                                add("%1L = get${paramName.capitalize()}.invoke(${from.name}.%1L),\n", paramName)
                            }
                        }
                    }
                }
                .add(")")
                .build())
        addFunction(funSpec.build())
    }

    private fun TypeSpec.Builder.generateEnumMapperFunction(
        from: DeclarationObject, to: DeclarationObject
    ) {
        val functionName = getFunctionName(from, to)
        val funSpec = FunSpec.builder(functionName)
            .addParameter(from.name, from.declaration.toClassName())
            .returns(to.declaration.toClassName())
        val clazzName = from.declaration.annotations.first().arguments.first().value.toString().substringAfterLast(".")
        val mapWith = enumValueOf<MapWith>(clazzName)
        funSpec.addCode(
            CodeBlock.builder()
                .add("return ${to.declaration.toClassName()}.values().first { it.${mapWith.identifier} == ${from.name}.${mapWith.identifier}  }")
                .build()
        )
        addFunction(funSpec.build())
    }

    private fun KSValueParameter.getExternalMapper(): KSType? {
        val type = type.resolve()
        return if (type.isEnum() || type.isClass()) {
            type
        } else null
    }

    private fun getFunctionName(from: DeclarationObject, to: DeclarationObject) = "map${from.suffix}To${to.suffix}"
    data class DeclarationObject(val declaration: KSClassDeclaration, val name: String, val suffix: String)

    companion object {
        private const val DTO_SUFFIX = "Dto"
        private const val DTO_MODEL = "dtoModel"

        private const val DOMAIN_SUFFIX = "Domain"
        private const val DOMAIN_MODEL = "domainModel"

        private const val MAPPER_PACKAGE_NAME = ".mappers"
    }
}