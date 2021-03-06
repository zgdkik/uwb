package com.zhilutec.services;

import com.zhilutec.dbs.pojos.RedisPolicy;

import java.util.List;

public interface IStrategyService {

    List<RedisPolicy> getRedisPolicies(String key);

    List<RedisPolicy> getRedisPolicies(Long tagId);

}
