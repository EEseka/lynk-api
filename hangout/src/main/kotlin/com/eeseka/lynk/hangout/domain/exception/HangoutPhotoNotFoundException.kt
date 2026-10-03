package com.eeseka.lynk.hangout.domain.exception

class HangoutPhotoNotFoundException(photoId: String) : RuntimeException("Photo with ID $photoId not found")
