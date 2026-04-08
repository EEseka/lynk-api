package com.eeseka.lynk.common.domain.exception

class StorageException(override val message: String?) : RuntimeException(message ?: "Unable to store file")