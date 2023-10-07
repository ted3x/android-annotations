package com.space.compiler.extensions

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.*
import com.space.annotations.DTO
import com.space.annotations.DomainModel
import java.util.*
import kotlin.reflect.KClass

@OptIn(KspExperimental::class)
fun <T : Annotation> KSFile.filterAnnotatedWith(annotationClazz: KClass<T>): List<KSClassDeclaration> {
    return declarations
        .filter { it.isAnnotationPresent(annotationClazz) }
        .filterIsInstance<KSClassDeclaration>()
        .toList()
}

@OptIn(KspExperimental::class)
internal fun KSType.isEnum(): Boolean {
    return declaration is KSClassDeclaration && (declaration as KSClassDeclaration).classKind == ClassKind.ENUM_CLASS && (
            declaration.isAnnotationPresent(DomainModel::class) || declaration.isAnnotationPresent(DTO::class))
}

@OptIn(KspExperimental::class)
internal fun KSType.isClass(): Boolean {
    return declaration is KSClassDeclaration && (declaration as KSClassDeclaration).classKind == ClassKind.CLASS && (
            declaration.isAnnotationPresent(DomainModel::class) || declaration.isAnnotationPresent(DTO::class))
}

internal fun String.decapitalize() = replaceFirstChar { it.lowercase(Locale.getDefault()) }
internal fun String.capitalize() = replaceFirstChar { it.uppercase(Locale.getDefault()) }

fun KSDeclaration.withoutSuffix(suffix: String): String {
    return simpleName.asString().removeSuffix(suffix)
}