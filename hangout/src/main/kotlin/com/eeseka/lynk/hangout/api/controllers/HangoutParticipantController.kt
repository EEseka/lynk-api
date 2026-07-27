package com.eeseka.lynk.hangout.api.controllers

import com.eeseka.lynk.hangout.api.dto.HangoutUserDto
import com.eeseka.lynk.hangout.api.mappers.toHangoutUserDto
import com.eeseka.lynk.hangout.service.HangoutUserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/participants")
class HangoutParticipantController(
    private val hangoutUserService: HangoutUserService
) {
    @GetMapping
    fun getHangoutUserByUsername(
        @RequestParam query: String
    ): HangoutUserDto {
        return hangoutUserService.getHangoutUserByUsername(query).toHangoutUserDto()
    }
}
