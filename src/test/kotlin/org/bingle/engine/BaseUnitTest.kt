package org.bingle.engine

import io.mockk.MockKAnnotations

open class BaseUnitTest {

    init {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }
}