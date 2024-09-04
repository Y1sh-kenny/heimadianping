package com.hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Override
	public Result sendCode(String phone, HttpSession session) {
		//校验
		if (RegexUtils.isPhoneInvalid(phone)) {
			return Result.fail("手机号格式不对");
		}
		//生成
		String code = RandomUtil.randomNumbers(6);
		//保存到Session
		//session.setAttribute("code",code);
		//保存到Redis
		stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
		//发送
		log.debug("code is " + code);
		return Result.ok();
	}

	@Override
	public Result login(LoginFormDTO loginForm, HttpSession session) {
		//验证手机号
		String phone = loginForm.getPhone();
		if (RegexUtils.isPhoneInvalid(phone)) {
			return Result.fail("手机号格式不对");
		}

		//验证验证码是否正确
		//Object cacheCode = session.getAttribute("code");
		Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);


		if(cacheCode == null || !cacheCode.toString().equals(loginForm.getCode())){
			return Result.fail("验证码错误");
		}

		//查询用户是否存在,不存在,则注册之
		User user = query().eq("phone",phone).one();
		if (user == null) {
			user = createUserWithPhone(phone);
		}
		//生成token
		String token = UUID.randomUUID().toString(true);

		UserDTO userDTO = new UserDTO();
		BeanUtils.copyProperties(user,userDTO);

		//将userDTO转换成MAP
		Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
				CopyOptions.create().setIgnoreNullValue(true)
						.setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
		//session.setAttribute("user",userDTO);
		String tokenKey = LOGIN_USER_KEY + token;
		stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
		stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

		return Result.ok(token);
	}

	private User createUserWithPhone(String phone) {
		User user = new User();
		user.setPhone(phone);
		user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +  RandomUtil.randomString(6));

		save(user);

		return user;
	}
}
