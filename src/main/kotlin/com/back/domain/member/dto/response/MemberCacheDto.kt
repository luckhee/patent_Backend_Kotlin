package com.back.domain.member.dto.response

import com.back.domain.member.entity.Member
import com.back.domain.member.entity.Role
import com.back.domain.member.entity.Status
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 캐시에 저장될 Member 정보 DTO
 * 민감한 정보(password, refreshToken)는 제외
 */
data class MemberCacheDto @JsonCreator constructor(
    @JsonProperty("id") val id: Long,
    @JsonProperty("email") val email: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("profileUrl") val profileUrl: String?,
    @JsonProperty("role") val role: Role,
    @JsonProperty("status") val status: Status
) {
    companion object {
        fun fromEntity(member: Member): MemberCacheDto {
            return MemberCacheDto(
                id = member.id,
                email = member.email,
                name = member.name,
                profileUrl = member.profileUrl,
                role = member.role,
                status = member.status
            )
        }
    }

    fun toEntity(): Member {
        // 캐시된 정보로 Member 객체 생성 (password, refreshToken은 빈 값)
        return Member(
            email = this.email,
            password = "", // 캐시에서는 비밀번호 정보 제외
            name = this.name,
            profileUrl = this.profileUrl,
            role = this.role,
            status = this.status
        ).apply {
            // ID는 reflection을 통해 설정하거나, 별도 방법으로 처리
            // 실제 사용시에는 ID가 필요한 경우만 사용
        }
    }
}
