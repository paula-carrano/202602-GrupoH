package com.cbo.players.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    public static final String CACHE_RANKING = "players::ranking";
    public static final String CACHE_CATALOG = "players::catalog";
    public static final String CACHE_EXTERNAL = "players::external";

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);
    private static final String UNKNOWN_CACHE_NAME = "desconocido";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${app.cache.ttl.default-minutes:30}") long defaultTtlMinutes,
            @Value("${app.cache.ttl.ranking-minutes:60}") long rankingTtlMinutes,
            @Value("${app.cache.ttl.catalog-minutes:15}") long catalogTtlMinutes,
            @Value("${app.cache.ttl.external-minutes:30}") long externalTtlMinutes) {

        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(Math.max(1, defaultTtlMinutes)))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(CACHE_RANKING, defaultConfig.entryTtl(Duration.ofMinutes(Math.max(1, rankingTtlMinutes))))
                .withCacheConfiguration(CACHE_CATALOG, defaultConfig.entryTtl(Duration.ofMinutes(Math.max(1, catalogTtlMinutes))))
                .withCacheConfiguration(CACHE_EXTERNAL, defaultConfig.entryTtl(Duration.ofMinutes(Math.max(1, externalTtlMinutes))))
                .enableStatistics()
                .build();
    }

    public static ObjectMapper buildRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.cbo.players.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .build();

        ObjectMapper.DefaultTypeResolverBuilder typer = new ObjectMapper.DefaultTypeResolverBuilder(
                ObjectMapper.DefaultTyping.NON_FINAL, ptv) {
            @Override
            public boolean useForType(JavaType t) {
                if (t.isPrimitive() || t.getRawClass() == String.class) {
                    return false;
                }
                if (t.getRawClass().isRecord() && t.getRawClass().getName().startsWith("com.cbo.players.")) {
                    return true;
                }
                return super.useForType(t);
            }
        };
        typer.init(JsonTypeInfo.Id.CLASS, null);
        typer.inclusion(JsonTypeInfo.As.PROPERTY);
        typer.typeProperty("@class");
        mapper.setDefaultTyping(typer);

        return mapper;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Error leyendo de cache [{}], key [{}]: {}. Continuando con ejecucion.",
                        cache != null ? cache.getName() : UNKNOWN_CACHE_NAME, key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Error escribiendo en cache [{}], key [{}]: {}. Continuando con ejecucion.",
                        cache != null ? cache.getName() : UNKNOWN_CACHE_NAME, key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Error invalidando key [{}] de cache [{}]: {}.",
                        key, cache != null ? cache.getName() : UNKNOWN_CACHE_NAME, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Error limpiando cache [{}]: {}.",
                        cache != null ? cache.getName() : UNKNOWN_CACHE_NAME, exception.getMessage());
            }
        };
    }
}
