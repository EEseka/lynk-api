package com.eeseka.lynk.hangout.domain.model

enum class HangoutPhotoStatus {
    PENDING,  // Upload slot handed out, files not confirmed yet. Counts toward the cap.
    READY     // Files confirmed in storage. Shown in the album.
}
