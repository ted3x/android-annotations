package com.space.annotations

@Target(AnnotationTarget.CLASS)
annotation class DTO(val mapWith: MapWith = MapWith.Name)

@Target(AnnotationTarget.CLASS)
annotation class DomainModel(val mapWith: MapWith = MapWith.Name)

enum class MapWith(val identifier: String) {
    Value("value"),
    Name("name")
}