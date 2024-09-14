package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author kenny
 * @version 1.0
 * @description: DONE
 * @date 2024/9/14 14:12
 */

@Data
public class RedisData {
	Object data;
	LocalDateTime expireTime;
}
