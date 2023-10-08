package com.space.test.feature_component

open class SingletonHolder<out T: SPBaseFeatureComponent>(creator: () -> T) {
    private var creator: (() -> T)? = creator
    @Volatile private var instance: T? = null
    open fun initAndGet(): T {
        val i = instance
        if (i != null) {
            return i
        }

        return synchronized(this) {
            val i2 = instance
            if (i2 != null) {
                i2
            } else {
                val created = creator!!()
                instance = created
                created
            }
        }
    }

    open fun init() {
        return synchronized(this) {
            val i2 = instance
            if (i2 != null) {
                val created = creator!!()
                instance = created
            }
        }
    }

    fun checkIfNotNull(): Boolean = instance != null

    fun get(): T = instance
        ?: throw IllegalStateException("You must call 'init() or initAndGet()' method first")

    open fun reset() {
        instance = null
    }
}