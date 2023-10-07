package com.space.test

import com.space.test.mappers.ModelOneMapper
import com.space.test.mappers.ModelTwoEnumMapper
import com.space.test.mappers.ModelTwoMapper

fun main() {
    val dto = ModelOneDto("123", "ted3x")
    val mapper = ModelOneMapper()
    val domainModel = mapper.mapDtoToDomain(dto)
    println(domainModel)

    val complexDto = ModelTwoDto("123", ModelTwoEnumDto.Disabled, 123)
    val complexMapper = ModelTwoMapper()
    val complexDomainModel = complexMapper.mapDtoToDomain(complexDto, ModelTwoEnumMapper()) {
        it.toString()
    }
    println(complexDomainModel)
}