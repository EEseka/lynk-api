package com.eeseka.lynk.infra.caching

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.kotlinModule
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig {

    @Bean
    fun cacheManager(
        connectionFactory: LettuceConnectionFactory
    ): RedisCacheManager {
        
        // Only allow these packages to be deserialized from Redis
        val polymorphicTypeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("java.util.") 
            .allowIfSubType("kotlin.collections.") 
            .allowIfSubType("com.eeseka.lynk.")
            .build()

        val objectMapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .findAndAddModules()
            .polymorphicTypeValidator(polymorphicTypeValidator)
            .activateDefaultTyping(polymorphicTypeValidator, DefaultTyping.NON_FINAL)
            .build()

        val defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1L))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJacksonJsonRedisSerializer(objectMapper)
                )
            )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withCacheConfiguration(
                "trending_spots",
                defaultCacheConfig.entryTtl(Duration.ofMinutes(15)) 
            )
            .transactionAware()
            .build()
    }
}