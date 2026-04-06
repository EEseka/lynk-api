package com.eeseka.lynk

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class LynkApplication

fun main(args: Array<String>) {
    runApplication<LynkApplication>(*args)
}
