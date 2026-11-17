package com.azimulkabir.actua.billing.website

internal object SandboxSupportReturn {
    fun accepts(uri: String?, direct: Boolean, debug: Boolean): Boolean =
        direct && debug && uri == "actua-supporter-sandbox://return"
}
