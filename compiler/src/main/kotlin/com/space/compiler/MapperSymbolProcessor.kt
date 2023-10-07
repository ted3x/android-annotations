package com.space.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.space.annotations.DTO
import com.space.annotations.DomainModel
import com.space.annotations.MapWith
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.*

class MapperSymbolProcessor(private val logger: KSPLogger, private val codeGenerator: CodeGenerator) : SymbolProcessor {
    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        for (file in resolver.getNewFiles()) {
            val dtoDeclarations = file.declarations
                .filter { it.isAnnotationPresent(DTO::class) }
                .filterIsInstance<KSClassDeclaration>()
                .toList()

            val domainDeclarations = file.declarations
                .filter { it.isAnnotationPresent(DomainModel::class) }
                .filterIsInstance<KSClassDeclaration>()
                .toList()


            val declarations = dtoDeclarations.mapNotNull { dtoDeclaration ->
                val declarationName =
                    dtoDeclaration.simpleName.asString().removeSuffix(DTO_SUFFIX)
                val domainDeclaration =
                    domainDeclarations.find { it.simpleName.asString().removeSuffix(DOMAIN_SUFFIX) == declarationName }
                if (domainDeclaration != null) dtoDeclaration to domainDeclaration
                else null
            }

            declarations.forEach { declaration ->
                val fileName: String = declaration.first.simpleName.asString().removeSuffix(DTO_SUFFIX)
                val filePackage: String = declaration.first.packageName.asString() + MAPPER_PACKAGE_NAME
                val dtoDeclaration = declaration.first
                val domainDeclaration = declaration.second

                FileSpec.builder(filePackage, "${fileName}Mapper_Gen")
                    .apply {
                        dtoDeclaration.checkForClassKind()
                        domainDeclaration.checkForClassKind()

                        val declarationImpl = "${fileName}Mapper"

                        addType(
                            TypeSpec.classBuilder(declarationImpl)
                                .addOriginatingKSFile(file)
                                .apply {
                                    if (dtoDeclaration.classKind == ClassKind.CLASS) {
                                        addDtoToDomainMapperFunction(dtoDeclaration, domainDeclaration)
                                        addDomainToDtoMapperFunction(dtoDeclaration, domainDeclaration)
                                    } else {
                                        addDtoToDomainMapperFunctionEnum(dtoDeclaration, domainDeclaration)
                                        addDomainToDtoMapperFunctionEnum(dtoDeclaration, domainDeclaration)
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

    private fun TypeSpec.Builder.addDtoToDomainMapperFunction(
        dto: KSClassDeclaration,
        domainModel: KSClassDeclaration
    ) {
        val funSpec = FunSpec.builder("mapDtoToDomain")
            .addParameter("dtoModel", dto.toClassName())
            .returns(domainModel.toClassName())
        funSpec.addCode(
            CodeBlock.builder()
                .add("return·%T(\n", domainModel.toClassName()).withIndent {
                    for (param in domainModel.primaryConstructor!!.parameters) {
                        val paramName = param.name!!.asString()
                        param.getExternalMapper()?.let {
                            val parameterName = it.toClassName().simpleName.removeSuffix(DOMAIN_SUFFIX)
                            funSpec.addParameter(
                                parameterName.decapitalize(),
                                ClassName("com.space.test.mappers", "${parameterName}Mapper")
                            )
                            add("%1L = ${parameterName.decapitalize()}.mapDtoToDomain(dtoModel.%1L),\n", paramName)
                        } ?: run {
                            val dtoParam =
                                dto.primaryConstructor!!.parameters.firstOrNull { it.name?.asString() == paramName }
                            if (dtoParam != null && dtoParam.type.resolve() == param.type.resolve())
                                add("%1L = dtoModel.%1L,\n", paramName)
                            else {
                                val receiverType = dtoParam?.type?.resolve()?.toTypeName()!!
                                val returnType = param.type.resolve().toTypeName()

                                // Create a function with a lambda type
                                val functionType = LambdaTypeName.get(
                                    parameters = arrayOf(receiverType),
                                    returnType = returnType
                                )
                                val spec = ParameterSpec.builder("get${paramName.capitalize()}", functionType).build()
                                funSpec.addParameter(spec)
                                add("%1L = get${paramName.capitalize()}.invoke(dtoModel.%1L),\n", paramName)
                            }
                        }
                    }
                }
                .add(")")
                .build())
        addFunction(funSpec.build())
    }

    private fun TypeSpec.Builder.addDomainToDtoMapperFunction(
        dto: KSClassDeclaration,
        domainModel: KSClassDeclaration
    ) {
        val funSpec = FunSpec.builder("mapDomainToDto")
            .addParameter("domainModel", domainModel.toClassName())
            .returns(dto.toClassName())
        funSpec.addCode(CodeBlock.builder()
            .add("return·%T(\n", dto.toClassName()).withIndent {
                for (param in dto.primaryConstructor!!.parameters) {
                    val paramName = param.name!!.asString()
                    param.getExternalMapper()?.let {
                        val parameterName = it.toClassName().simpleName.removeSuffix(DTO_SUFFIX)
                        funSpec.addParameter(
                            parameterName.decapitalize(),
                            ClassName("com.space.test.mappers", "${parameterName}Mapper")
                        )
                        add("%1L = ${parameterName.decapitalize()}.mapDomainToDto(domainModel.%1L),\n", paramName)
                    } ?: run {
                        val domainParam =
                            domainModel.primaryConstructor!!.parameters.firstOrNull { it.name?.asString() == paramName }
                        if (domainParam != null && domainParam.type.resolve() == param.type.resolve())
                            add("%1L = domainModel.%1L,\n", paramName)
                        else {
                            val receiverType = domainParam?.type?.resolve()?.toTypeName()!!
                            val returnType = param.type.resolve().toTypeName()

                            // Create a function with a lambda type
                            val functionType = LambdaTypeName.get(
                                parameters = arrayOf(receiverType),
                                returnType = returnType
                            )
                            val spec = ParameterSpec.builder("get${paramName.capitalize()}", functionType).build()
                            funSpec.addParameter(spec)
                            add("%1L = get${paramName.capitalize()}.invoke(domainModel.%1L),\n", paramName)
                        }
                    }
                }
            }
            .add(")")
            .build())
        addFunction(funSpec.build())
    }

    private fun TypeSpec.Builder.addDtoToDomainMapperFunctionEnum(
        dto: KSClassDeclaration,
        domainModel: KSClassDeclaration
    ) {
        val funSpec = FunSpec.builder("mapDtoToDomain")
            .addParameter("dtoModel", dto.toClassName())
            .returns(domainModel.toClassName())
        val clazzName = dto.annotations.first().arguments.first().value.toString().substringAfterLast(".")
        val mapWith = enumValueOf<MapWith>(clazzName)
        funSpec.addCode(
            CodeBlock.builder()
                .add("return ${domainModel.toClassName()}.values().first { it.${mapWith.identifier} == dtoModel.${mapWith.identifier}  }")
                .build()
        )
        addFunction(funSpec.build())
    }

    private fun TypeSpec.Builder.addDomainToDtoMapperFunctionEnum(
        dto: KSClassDeclaration,
        domainModel: KSClassDeclaration
    ) {
        val funSpec = FunSpec.builder("mapDomainToDto")
            .addParameter("domainModel", domainModel.toClassName())
            .returns(dto.toClassName())
        val clazzName = dto.annotations.first().arguments.first().value.toString().substringAfterLast(".")
        val mapWith = enumValueOf<MapWith>(clazzName)
        funSpec.addCode(
            CodeBlock.builder()
                .add("return ${dto.toClassName()}.values().first { it.${mapWith.identifier} == domainModel.${mapWith.identifier} }")
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

    @OptIn(KspExperimental::class)
    private fun KSType.isEnum(): Boolean {
        return declaration is KSClassDeclaration && (declaration as KSClassDeclaration).classKind == ClassKind.ENUM_CLASS && (
                declaration.isAnnotationPresent(DomainModel::class) || declaration.isAnnotationPresent(DTO::class))
    }

    @OptIn(KspExperimental::class)
    private fun KSType.isClass(): Boolean {
        return declaration is KSClassDeclaration && (declaration as KSClassDeclaration).classKind == ClassKind.CLASS && (
                declaration.isAnnotationPresent(DomainModel::class) || declaration.isAnnotationPresent(DTO::class))
    }

    private fun KSClassDeclaration.checkForClassKind() {
        if (classKind != ClassKind.CLASS && classKind != ClassKind.ENUM_CLASS) {
            logger.error("${this.annotations.first()} can be declared only on interfaces", this)
        }
    }

    private fun String.decapitalize() = replaceFirstChar { it.lowercase(Locale.getDefault()) }
    private fun String.capitalize() = replaceFirstChar { it.uppercase(Locale.getDefault()) }
}

const val DTO_SUFFIX = "Dto"
const val DOMAIN_SUFFIX = "Domain"
const val MAPPER_PACKAGE_NAME = ".mappers"