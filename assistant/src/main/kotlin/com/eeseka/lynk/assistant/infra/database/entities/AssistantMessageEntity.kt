package com.eeseka.lynk.assistant.infra.database.entities

import com.eeseka.lynk.assistant.domain.model.AssistantFailureReason
import com.eeseka.lynk.assistant.domain.model.AssistantMessageRole
import com.eeseka.lynk.assistant.domain.model.AssistantMessageStatus
import com.eeseka.lynk.common.domain.type.AssistantMessageId
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "assistant_messages",
    schema = "assistant_service",
    indexes = [
        // The two partial indexes on PENDING rows live only in V9; JPA cannot express them
        Index(
            name = "idx_assistant_messages_hangout_id_user_id_created_at",
            columnList = "hangout_id, user_id, created_at DESC"
        ),
        Index(name = "idx_assistant_messages_user_id", columnList = "user_id")
    ]
)
class AssistantMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: AssistantMessageId? = null,

    @Column(nullable = false)
    var hangoutId: HangoutId,

    @Column(nullable = false)
    var userId: UserId,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: AssistantMessageRole,

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    var status: AssistantMessageStatus? = null, // Only answers have one; a question is never pending

    @Column(nullable = false, columnDefinition = "text")
    var content: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    var failureReason: AssistantFailureReason? = null,

    @Column(nullable = true)
    var inputTokens: Int? = null,

    @Column(nullable = true)
    var outputTokens: Int? = null,

    @Column(nullable = true)
    var searchCount: Int? = null,

    @OneToMany(
        mappedBy = "message",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    @OrderBy("position")
    var spots: MutableList<AssistantSpotEntity> = mutableListOf(),

    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)
