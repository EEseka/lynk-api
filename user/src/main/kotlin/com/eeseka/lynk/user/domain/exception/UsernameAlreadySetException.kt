package com.eeseka.lynk.user.domain.exception

class UsernameAlreadySetException : RuntimeException("Username is chosen once when the profile is completed and cannot be changed.")