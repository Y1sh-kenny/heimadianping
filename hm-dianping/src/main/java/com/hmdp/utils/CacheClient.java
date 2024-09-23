package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RedisData;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
	private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
	public CacheClient(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}

	public void set(String key, Object value, Long time, TimeUnit timeUnit) {
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
	}


	//逻辑过期 -- 在设置数据库传入过期字段,且不设置
	public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
		RedisData redisData = new RedisData();
		redisData.setData(value);
		redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
	}

	//缓存穿透
	public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbCallBack, Long time, TimeUnit unit) {
		String key = keyPrefix + id;
		String json = stringRedisTemplate.opsForValue().get(key);
		if (StrUtil.isNotBlank(json)) {
			return JSONUtil.toBean(json, type);
		}
		//判断命中的是空值,返回null
		if (json != null) {
			return null;
		}

		//没有命中,查询数据库,查到了,添加缓存,返回,没查到,添加空缓存,返回null
		R r = dbCallBack.apply(id);
		if (r == null) {
			stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
			return null;
		}
		set(key, r, time, unit);
		return r;

	}


	//缓存击穿 -- 逻辑过期的缓存击穿,是需要程序员提前配置可能的热点数据的.
	public <R, ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbCallBack, Long time, TimeUnit unit) {
		String key = keyPrefix + id;
		//从Redis里面查
		String json = stringRedisTemplate.opsForValue().get(key);
		if (StrUtil.isBlank(json)) {
			return null;
		}

		//查看是否过期
		RedisData redisData = JSONUtil.toBean(json, RedisData.class);
		R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
		LocalDateTime expireTime = redisData.getExpireTime();
		if (expireTime.isAfter(LocalDateTime.now())) {
			//没过期
			return r;
		}
		//过期了
		//1.获取锁,启动线程更新
		String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
		boolean isLock = tryLock(lockKey);
		//1.1拿到锁,首先再次从Redis冲查询数据,检查是否过期,反之上一个拿到锁的人已经更新了.
		if (isLock) {
			json = stringRedisTemplate.opsForValue().get(key);
			if (StrUtil.isBlank(json)) {
				return null;
			}
			//查看是否过期 -- 双重检测
			redisData = JSONUtil.toBean(json, RedisData.class);
			r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
			expireTime = redisData.getExpireTime();
			if (expireTime.isAfter(LocalDateTime.now())) {
				//没过期
				return r;
			}
			//ok,完全过期了
			CACHE_REBUILD_EXECUTOR.submit(() -> {
				//重建
				try {
					R r_new = dbCallBack.apply(id);
					setWithLogicExpire(key,r_new,time,unit);
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					unlock(lockKey);
				}

			});
		}

		//返回旧数据.
		return r;
	}

	private boolean tryLock(String key)
	{
		Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
		return BooleanUtil.isTrue(flag);
	}

	private void unlock(String key){
		stringRedisTemplate.delete(key);
	}
}
