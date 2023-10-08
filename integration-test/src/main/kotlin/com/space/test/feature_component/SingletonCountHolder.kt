package com.space.test.feature_component

open class SingletonCountHolder<T : SPBaseFeatureComponent>(creator: () -> T) : SingletonHolder<T>(creator) {
    private var count = 0

    override fun initAndGet(): T {
        count++
        return super.initAndGet()
    }

    override fun init() {
        super.init()
        count++
    }

    fun getCount() = count

    override fun reset() {
        count--
        if(count > 0) return
        else super.reset()
    }
}