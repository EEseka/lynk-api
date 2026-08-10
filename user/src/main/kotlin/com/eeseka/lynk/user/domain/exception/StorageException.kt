package com.eeseka.lynk.user.domain.exception

class StorageException(override val message: String?) : RuntimeException(message ?: "Unable to store file")