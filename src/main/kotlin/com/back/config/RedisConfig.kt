package com.back.config

import com.back.domain.chat.redis.listener.RedisMessageSubscriber
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig(
    @Value("\${spring.data.redis.host}") private val redisHost: String,
    @Value("\${spring.data.redis.port}") private val redisPort: Int
) {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory =
        LettuceConnectionFactory(redisHost, redisPort)

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> =
        RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJackson2JsonRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer()
        }

    @Bean
    fun messageListener(subscriber: RedisMessageSubscriber): MessageListenerAdapter =
        MessageListenerAdapter(subscriber, "onMessage")

    @Bean
    fun redisContainer(
        connectionFactory: RedisConnectionFactory,
        messageListener: MessageListenerAdapter
    ): RedisMessageListenerContainer =
        RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
            addMessageListener(messageListener, chatTopic())
        }

    @Bean
    fun chatTopic(): ChannelTopic = ChannelTopic("chat-messages")

    // Cache Manager 설정
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        // 기본 캐시 설정
        val defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))  // 기본 TTL 30분
            // 저장/조회할때 쓰는 방식
            .serializeKeysWith(
                org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                    .fromSerializer(StringRedisSerializer())
            )//키를 넣을 때 사람이 읽기 쉽게 저장
            .serializeValuesWith(
                org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                    .fromSerializer(GenericJackson2JsonRedisSerializer())
            )// 값을 넣을 때는 직렬화해서 저장 이거 같은 경우 Json으로 직렬화

        // 캐시별 개별 설정
        val cacheConfigurations = mapOf(
            "memberCache" to defaultCacheConfig.entryTtl(Duration.ofHours(1)), // Member 정보는 1시간
            "chatRoomCache" to defaultCacheConfig.entryTtl(Duration.ofMinutes(15)), // 채팅방 목록은 15분
            "messageCache" to defaultCacheConfig.entryTtl(Duration.ofMinutes(5))   // 메시지는 5분
        )

        return RedisCacheManager.builder(connectionFactory)//커넥션팩토리는 그 레디스 서버와의 구성
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}
