package com.space.annotations

@Target(AnnotationTarget.CLASS)
annotation class DTO(val mapWith: MapWith = MapWith.Name)

@Target(AnnotationTarget.CLASS)
annotation class DomainModel(val mapWith: MapWith = MapWith.Name)

@Target(AnnotationTarget.FIELD)
annotation class DTOFieldName(val value: String)

@Target(AnnotationTarget.FIELD)
annotation class DomainFieldName(val value: String)

enum class MapWith(val identifier: String) {
    Value("value"),
    Name("name")
}