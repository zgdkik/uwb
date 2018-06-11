package com.zhilutec.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.zhilutec.services.ILoginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zhilutec.common.result.Result;
import com.zhilutec.common.result.ResultCode;
import com.zhilutec.common.utils.JWTUtil;
import com.zhilutec.common.utils.MD5Util;
import com.zhilutec.dbs.entities.User;
import com.zhilutec.services.IUserService;

import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;

@CrossOrigin(origins = "*", maxAge = 3600, methods = {RequestMethod.POST})
@RestController
@RequestMapping(value = "/user")
@EnableAutoConfiguration
@Api(value = "Login")
public class LoginController {

    public final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private ValueOperations<String, Object> valueOperations;

    @Autowired
    private HashOperations<String, String, Object> hashOperations;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ILoginService loginService;

    @ApiOperation(value = "登录接口", notes = "用户登录接口<br/>密码会被加密<br><hr/>", response = String.class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "username", value = "用户名", required = true, dataType = "String", paramType = "body", defaultValue = "admin"),
            @ApiImplicitParam(name = "password", value = "密码", required = true, dataType = "String", paramType = "body", defaultValue = "123456")})
    @RequestMapping(value = "/login", method = {RequestMethod.POST}, produces = "application/json;charset=UTF-8")
    public String login(HttpServletRequest request, HttpServletResponse response, @RequestBody JSONObject requestJson) {
        logger.info("============用户登录====================");
        logger.info("requestJson:" + requestJson.toJSONString());
        try {
            return loginService.login(request, requestJson);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("用户登录出现异常").toJSONString();
        }

    }

    @ApiOperation(value = "登录验证", notes = "根据TOken状态判断用户是否登录成功<br><hr/>", response = String.class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "token", value = "Token", required = true, dataType = "String", paramType = "body")})
    @RequestMapping(value = "/islogin", method = {RequestMethod.POST}, produces = "application/json;charset=UTF-8")
    public String islogin(@RequestBody JSONObject requestJson) {
        logger.info("============校验Token====================");
        logger.info("requestJson:" + requestJson.toJSONString());
        try {
            return loginService.isLogin(requestJson);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("请登录系统").toJSONString();
        }
    }

    @ApiOperation(value = "用户退出", notes = "用户退出<br><hr/>", response = String.class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "token", value = "Token", required = true, dataType = "String", paramType = "body")})
    @RequestMapping(value = "/logout", method = {RequestMethod.POST}, produces = "application/json;charset=UTF-8")
    public String logout(@RequestBody JSONObject requestJson) {
        logger.info("============退出登录====================");
        logger.info("requestJson:" + requestJson.toJSONString());
        try {
            return loginService.logout(requestJson);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("用户退出异常").toJSONString();
        }
    }

}
