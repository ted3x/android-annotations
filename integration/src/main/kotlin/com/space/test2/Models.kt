package com.space.test2

import com.space.annotations.*
import com.space.test.ModelTwoEnumDomainModel
import com.space.test.ModelTwoEnumDto

@DTO
data class TestClassDto(
    val value: String,
    val enum: ModelTwoEnumDto,
    @DomainFieldName("d")
    val sd: String,
    @DomainFieldName("testString")
    val testInt: Int
)

@DomainModel
data class TestClassDomainModel(
    val value: String,
    val enum: ModelTwoEnumDomainModel,
    @DTOFieldName("sd")
    val d: String,
    @DTOFieldName("testInt")
    val testString: String
)

@DTO
@MapsTo(TestClassAnotherDomainModel::class)
data class TestClassAnotherDto(
    val value: String,
    val enum: ModelTwoEnumDto,
    @DomainFieldName("d")
    val sd: String,
    @DomainFieldName("testString")
    val testInt: Int
)

data class TestClassAnotherDomainModel(
    val value: String,
    val enum: ModelTwoEnumDomainModel,
    val d: String,
    val testString: String
)