package com.creatotronik.dtls

import java.io.InputStream

// This is for desktop java, see AndroidResourceUtil for Androids
// Note we want resources in this package, not bcdtls

class JavaResourceUtil : IResourceUtil {
    override fun namedResourceStream(name: String): InputStream {
        return JavaResourceUtil::class.java.getResourceAsStream(name)
    }

}