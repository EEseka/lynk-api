package com.eeseka.lynk.common.domain

object ValidationPatterns {
    // Text meant for one line, like a name or a short note: anything but a line break
    const val SINGLE_LINE = "[^\\r\\n\\u0085\\u2028\\u2029]*"
}
