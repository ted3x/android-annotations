package com.space.compiler.mapper

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.space.compiler.mapper.MapperSymbolProcessor

class MapperSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return MapperSymbolProcessor(environment.logger, environment.codeGenerator)
    }
}