package com.back.domain.chat.chat.repository

import com.back.domain.chat.chat.entity.RoomParticipant
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RoomParticipantRepository : JpaRepository<RoomParticipant, Long> {

    fun existsByChatRoomIdAndMemberIdAndIsActiveTrue(chatRoomId: Long, memberId: Long): Boolean

    fun findByChatRoomIdAndIsActiveTrue(chatRoomId: Long): List<RoomParticipant>

    @Query("SELECT rp FROM RoomParticipant rp " +
            "JOIN FETCH rp.chatRoom cr " +
            "LEFT JOIN FETCH cr.post " +
            "WHERE rp.member.id = :memberId AND rp.isActive = true " +
            "ORDER BY rp.createdAt DESC")
    fun findByMemberIdAndIsActiveTrueOrderByCreatedAtDesc(@Param("memberId") memberId: Long): List<RoomParticipant>

    fun findByChatRoomIdAndMemberIdAndIsActiveTrue(chatRoomId: Long, id: Long): Optional<RoomParticipant>

    fun existsByChatRoomIdAndIsActiveTrue(chatRoomId: Long): Boolean

    // 활성/비활성 무관하게 채팅방의 모든 참여자 조회
    fun findByChatRoomId(chatRoomId: Long): List<RoomParticipant>
}
