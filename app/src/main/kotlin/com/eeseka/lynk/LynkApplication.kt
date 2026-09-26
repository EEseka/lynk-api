package com.eeseka.lynk

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class LynkApplication

fun main(args: Array<String>) {
    runApplication<LynkApplication>(*args)
}
