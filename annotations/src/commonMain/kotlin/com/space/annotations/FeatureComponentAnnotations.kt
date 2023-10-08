package com.space.annotations

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
annotation class FeatureComponent(val qualifier: KClass<*>, val featureModule: KClass<*>)