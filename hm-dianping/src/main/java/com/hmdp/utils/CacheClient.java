package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * @author kenny
 * @version 1.0
 * @description: TODO
 * @date 2024/9/14 15:24
 */
@Component
@Slf4j
public class CacheClient {
	private StringRedisTemplate stringRedisTemplate;

	public CacheClient(StringRedisTemplate stringRedisTemplate){
		this.stringRedisTemplate = stringRedisTemplate;
	}

	public void set(String key, Object value, Long time, TimeUnit timeUnit){
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
	}


	//逻辑过期 -- 在设置数据库传入过期字段,且不设置
	public void setWithLogicExpire(String key,Object value,Long time,TimeUnit unit){
		RedisData redisData = new RedisData();
		redisData.setData(value);
		redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

		stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
	}

	//缓存穿透



	//缓存击穿
}
