package com.eeseka.lynk.spot.domain.exception

class SpotNotFoundException(spotId: String) : RuntimeException("Spot with ID $spotId not found")