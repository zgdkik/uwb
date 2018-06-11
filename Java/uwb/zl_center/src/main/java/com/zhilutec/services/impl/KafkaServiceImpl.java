package com.zhilutec.services.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zhilutec.common.utils.ConstantUtil;
import com.zhilutec.common.utils.ZlTimeUtil;
import com.zhilutec.dbs.entities.*;
import com.zhilutec.dbs.pojos.RedisPolicy;
import com.zhilutec.services.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Time;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 手环消息处理核心代码
 * 一条坐标消息可能会产生多个围栏报警,此时最终生成的坐标消息会带最后一个报警的状态和level
 * 兼容引擎的直接报警消息
 * 兼容引擎的状态消息处理，电量,心率,SOS,腕带报警
 * 坐标中的心率，电量来源于下条状态消息
 * 电量和心率报警消息与上一次的坐标消息拼装
 * SOS状态，腕带发生一次变化记为一次SOS消息
 */

@Service("kafkaServiceImpl")
public class KafkaServiceImpl implements IKafkaService {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Resource
    IPolygonService polygonService;
    @Resource
    IStrategyService strategyService;

    @Resource
    IPersonService personService;
    @Resource
    IFenceService fenceService;

    @Resource
    IDepartmentService departmentService;

    @Resource
    IWarningService warningService;

    @Resource
    IStatusService statusService;

    @Resource
    @Qualifier("levelServiceImpl")
    ILevelService levelService;

    @Resource
    @Qualifier("positionServiceImpl")
    IPositionService positionService;

    @Resource
    ICoordinateService coordinateService;

    @Resource
    protected HashOperations<String, String, Integer> hashOperations;

    /**
     * 根据坐标点，与区域，策略的关系来判断是否报警
     */
    private Map<String, Object> handlePoint(Coordinate coordinate) throws InterruptedException {
        List<Warning> warnings = new ArrayList<>();
        Map resultMap = new HashMap();
        //从坐标消息中，获取策略key
        Long tagId = coordinate.getTagId();
        //找不到消息对应人员则不拼装消息
        Person person = personService.getPerson(tagId);
        if (person == null) {
            resultMap = null;
            return resultMap;
        }

        Department department = departmentService.getCache(person.getDepartmentCode());

        //给coordinate添加部门属性
        this.setCoodinateAttr(coordinate, person, department, person.getLevelCode(), person.getPositionCode());

        //将最近一次坐标信息缓存起来
        coordinateService.addRedisCoordinate(coordinate, ConstantUtil.REDIS_DEFAULT_TTL);
        //从坐标消息中,获取消息时间戳
        Long timestamp = coordinate.getTimestamp();
        //从坐标消息中,提取week
        Integer currWeek = ZlTimeUtil.getWeek(timestamp * 1000) - 1;
        //从坐标消息中 提取时间
        Time currTime = ZlTimeUtil.getTime(timestamp * 1000);

        //调取策略缓存
        // List<RedisPolicy> redisPolicies = strategyService.getRedisPolicies(tagId);

        //当redisPolicies为null再查询一次
        // if (redisPolicies == null) {
        //     Long gap = 5L;
        //     Thread.sleep(gap);
        //     redisPolicies = strategyService.getRedisPolicies(tagId);
        //     logger.info("sleep " + gap + " ms 后获取到策略:" + redisPolicies);
        // }

        //调取策略缓存
        int i = 1;
        List<RedisPolicy> redisPolicies = new ArrayList<>();
        while (i < 3) {
            redisPolicies = strategyService.getRedisPolicies(tagId);
            if (redisPolicies == null || redisPolicies.size() == 0) {
                Long gap = 5L;
                Thread.sleep(gap);
                redisPolicies = strategyService.getRedisPolicies(tagId);
                logger.info("sleep " + gap * i + " ms 后获取到策略:" + redisPolicies);
            } else {
                break;
            }
            i++;
        }

        for (RedisPolicy redisPolicy : redisPolicies) {
            Warning warning = this.handleCoordinate(coordinate, tagId, currWeek, currTime, redisPolicy);
            if (warning != null)
                warnings.add(warning);
        }
        //设置坐标消息的类型和坐标消息对应的的报警级别
        this.setLevelType(coordinate, tagId);
        //设置心率和电量信息
        this.setStatus(coordinate, tagId);
        resultMap.put("coordinate", coordinate);
        resultMap.put("warnings", warnings);
        return resultMap;
    }

    //设置坐标消息的类型和坐标消息对应的的报警级别
    private void setLevelType(Coordinate coordinate, Long tagId) {
        //处理消息的level和type,消息的type与报警类型有关,多个报警类型只取级别高的报警值来设置type
        Integer level = coordinate.getLevel();
        String powerAlarm = statusService.redisGet(ConstantUtil.POWER_ALARM_KEY_PRE, tagId);
        String heartAlarm = statusService.redisGet(ConstantUtil.HEART_ALARM_KEY_PRE, tagId);
        String wristAlarm = statusService.redisGet(ConstantUtil.WRISTLET_ALARM_KEY_PRE, tagId);
        String sosAlarm = statusService.redisGet(ConstantUtil.SOS_ALARM_KEY_PRE, tagId);
        if (level == null || level != ConstantUtil.ALARM_URGEN.intValue()) {
            if (heartAlarm != null) {
                Warning heartWarning = JSONObject.parseObject(heartAlarm, Warning.class);
                Integer heartLevel = heartWarning.getLevel();
                if (heartLevel != null) {
                    if (level == null) {
                        coordinate.setLevel(heartLevel);
                        coordinate.setType(ConstantUtil.COOR_HEART);
                    } else if (heartLevel > level) {
                        coordinate.setLevel(heartLevel);
                        coordinate.setType(ConstantUtil.COOR_HEART);
                    }
                }
            } else if (powerAlarm != null) {
                Warning powerWarning = JSONObject.parseObject(powerAlarm, Warning.class);
                Integer powerLevel = powerWarning.getLevel();
                level = coordinate.getLevel();
                if (powerLevel != null) {
                    if (level == null) {
                        coordinate.setLevel(powerLevel);
                        coordinate.setType(ConstantUtil.COOR_HEART);
                    } else if (powerLevel > level) {
                        coordinate.setLevel(powerLevel);
                        coordinate.setType(ConstantUtil.COOR_POWER);
                    }
                }
            } else if (wristAlarm != null) {
                Warning wristWarning = JSONObject.parseObject(wristAlarm, Warning.class);
                Integer wristLevel = wristWarning.getLevel();
                level = coordinate.getLevel();
                if (wristLevel != null) {
                    if (level == null) {
                        coordinate.setLevel(wristLevel);
                        coordinate.setType(ConstantUtil.COOR_WRISTLET);
                    } else if (wristLevel > level) {
                        coordinate.setLevel(wristLevel);
                        coordinate.setType(ConstantUtil.COOR_WRISTLET);
                    }
                }
            } else if (sosAlarm != null) {
                Warning sosWarning = JSONObject.parseObject(sosAlarm, Warning.class);
                Integer sosLevel = sosWarning.getLevel();
                level = coordinate.getLevel();
                if (sosLevel != null) {
                    if (level == null) {
                        coordinate.setLevel(sosLevel);
                        coordinate.setType(ConstantUtil.COOR_SOS);
                    } else if (sosLevel > level) {
                        coordinate.setLevel(sosLevel);
                        coordinate.setType(ConstantUtil.COOR_SOS);
                    }
                }
            }
        }
    }

    //设置状态值到坐标消息
    private void setStatus(Coordinate coordinate, Long tagId) {
        String statusStr = statusService.redisGet(ConstantUtil.STATUS_KEY_PRE, tagId);
        Status status = JSONObject.parseObject(statusStr, Status.class);
        if (statusStr != null && !statusStr.isEmpty()) {
            coordinate.setPower(status.getPower());
            coordinate.setHeartRate(status.getHeart());
        }
    }

    //处理坐标与围栏关系
    private Warning handleCoordinate(Coordinate coordinate, Long tagId, Integer currWeek, Time currTime, RedisPolicy redisPolicy) {
        Warning warning = null;
        String fenceCode = redisPolicy.getFenceCode();
        Fence redisfence = fenceService.getFence(fenceCode);
        if (redisfence == null)
            return null;

        //策略生效时间段
        Time startTime = redisPolicy.getStartTime();
        Time finishTime = redisPolicy.getFinishTime();
        //判断消息与策略的时间段是否匹配
        Boolean isTimeBetween = null;
        try {
            isTimeBetween = ZlTimeUtil.timeBetween(startTime.toString(), finishTime.toString(), currTime.toString());
        } catch (ParseException e) {
            e.printStackTrace();
        }

        //判断消息的日期与策略重复week值是否匹配
        List weeks = redisPolicy.getTimeValues();
        Boolean isWeekIn = weeks.contains(currWeek.toString());
        if (isTimeBetween != null && isTimeBetween && isWeekIn) {
            //从缓存中获取坐标
            String points = redisfence.getPoints();
            //设置当前围栏名
            coordinate.setFenceName(redisfence.getFenceName());
            coordinate.setFenceCode(redisfence.getFenceCode());
            //设置当前策略名
            coordinate.setStrategyName(redisPolicy.getStrategyName());
            coordinate.setStrategyCode(redisPolicy.getStrategyCode());
            //多边形算法判断构建围栏对象,并判断坐标与点的关系
            if (points != null && !points.isEmpty()) {
                //判断区域类型如果为矩形和多边形使用多边形算法
                if (redisfence.getType().intValue() == ConstantUtil.POLYGON || redisfence.getType().intValue() == ConstantUtil.RECTANGLE) {
                    Boolean isIn = polygonService.isInFence(points, coordinate.getPosX(), coordinate.getPosY());
                    //策略行为
                    Integer forbidden = redisPolicy.getForbidden();
                    int forbiddenValue = forbidden.intValue();
                    int inValue = ConstantUtil.FENCE_IN.intValue();
                    int outValue = ConstantUtil.FENCE_OUT.intValue();
                    //策略报警级别
                    Integer level = redisPolicy.getLevel();
                    coordinate.setLevel(level);
                    //获取历史报警
                    List<String> warningLsOld = (List<String>) warningService.getRedisStrList(tagId);
                    if (isIn) {
                        //点在围栏中
                        //forbidden 0 禁止进入
                        if (forbiddenValue == inValue) {
                            //不符合策略，查看是否之前有过相同报警，有则不添加报警，无则添加报警，并缓存报警,并在坐标添加报警状态
                            String warningMsg = ConstantUtil.FENCE_ENTER + redisfence.getFenceName();
                            warning = this.createfenceAlarm(warningMsg, warningLsOld, coordinate);
                        } else if (forbiddenValue == outValue) {
                            //forbidden 1 禁止进出
                            //符合策略,查看是否有旧报警,有旧报警,解除报警
                            if (warningLsOld != null && warningLsOld.size() > 0) {
                                warning = this.cancelFenceAlarm(coordinate, warningLsOld);
                            }
                        }
                    } else {
                        //点在围栏外
                        //forbidden 0 禁止进入
                        if (forbiddenValue == inValue) {
                            //符合策略,查看是否有旧报警,有旧报警,解除报警
                            if (warningLsOld != null && warningLsOld.size() > 0) {
                                warning = this.cancelFenceAlarm(coordinate, warningLsOld);
                            }
                        } else if (forbiddenValue == outValue) {
                            //forbidden 1 禁止进出
                            //不符合策略，查看是否之前有过相同报警，有则不添加报警，无则添加报警，并缓存报警,并在坐标添加报警状态
                            String warningMsg = ConstantUtil.FENCE_LEAVE + redisfence.getFenceName();
                            warning = this.createfenceAlarm(warningMsg, warningLsOld, coordinate);
                        }
                    }
                }
            }
        }
        return warning;
    }

    @Override
    public Map<String, Object> handlePoints(List<ConsumerRecord<String, String>> records) throws InterruptedException {
        Map<String, Object> resultMap = new HashMap();
        List<Coordinate> coordinates = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            String kafkaMsg = record.value().toString();
            JSONObject msgObj = JSON.parseObject(kafkaMsg);
            Integer type = msgObj.getInteger("type");

            //只处理type 2 消息
            if (type == ConstantUtil.ENGINE_COOR) {
                Coordinate coordinate = JSON.parseObject(record.value().toString(), Coordinate.class);
                Map rsMap = this.handlePoint(coordinate);
                if (rsMap != null) {
                    Coordinate coordinate1 = (Coordinate) rsMap.get("coordinate");
                    List<Warning> warnings1 = (List<Warning>) rsMap.get("warnings");
                    if (coordinate1 != null)
                        coordinates.add(coordinate1);
                    if (warnings1 != null && warnings1.size() > 0)
                        warnings.addAll(warnings1);
                }
            } else if (type == ConstantUtil.ENGINE_ARLARM) {
                //兼容定位引擎直接上报的报警
                this.handleAlarm(coordinates, warnings, msgObj);
            } else if (type == ConstantUtil.ENGINE_STATUS) {
                //处理状态消息
                List<Warning> statusWarnings = this.handleStatus(msgObj);
                if (statusWarnings != null && statusWarnings.size() > 0) {
                    warnings.addAll(statusWarnings);
                }
            }
        }

        List<String> coorStr = new ArrayList<>();
        if (coordinates != null && coordinates.size() > 0) {
            for (Coordinate coor : coordinates) {
                coorStr.add(JSON.toJSONString(coor));
            }
        }

        List<String> warningStr = new ArrayList<>();
        if (warnings != null && warnings.size() > 0) {
            for (Warning warning : warnings) {
                warningStr.add(JSON.toJSONString(warning));
            }
        }

        resultMap.put("coordinates", coorStr);
        resultMap.put("warnings", warningStr);
        return resultMap;
    }


    //处理直接报警
    private void handleAlarm(List<Coordinate> coordinates, List<Warning> warnings, JSONObject msgObj) {
        Long tagId = msgObj.getLong("tag_id");
        Integer op = msgObj.getInteger("op");
        //获取历史报警列表
        List<Warning> warningLsOld = (List<Warning>) warningService.getRedisWarningList(tagId);
        if (op.intValue() == 1) {
            Coordinate coordinate = coordinateService.getReidsCoordinate(tagId);
            if (coordinate != null) {
                this.setCoordinate(coordinate, msgObj);
                coordinates.add(coordinate);
                Warning warning = this.createWarning(coordinate.getPersonName(), msgObj, warningLsOld);
                if (warning != null)
                    warnings.add(warning);
            } else {
                //找不到历史坐标消息就只发送报警
                Person person = personService.getPerson(tagId);
                if (person != null) {
                    Warning warning = this.createWarning(person.getPersonName(), msgObj, warningLsOld);
                    if (warning != null)
                        warnings.add(warning);
                }
            }
        } else {
            //收到取消报警时删除缓存中报警
            Integer warningType = msgObj.getInteger("warning_type");
            for (Warning warning : warningLsOld) {
                Long redisTagId = warning.getTagId();
                Integer redisType = warning.getType();
                if (tagId.longValue() == redisTagId.longValue() && warningType.intValue() == redisType.intValue()) {
                    //删除旧报警缓存
                    warningService.deleteRedisWarning(redisTagId, warning);
                    //设置报警取消消息
                    warning.setOp(ConstantUtil.ALARM_OFF);
                    warning.setTimestamp(msgObj.getLong("timestamp"));
                    warnings.add(warning);
                    break;
                }
            }
        }
    }

    //{"tag_id":0,"power":20,"heart":75,"type":7,"sos":1,"wristlet":0,"move":0,"timestamp":1525672722}*
    //处理状态消息,前将消息写入redis缓存中
    private List<Warning> handleStatus(JSONObject msgObj) {
        List<Warning> statusWarnings = new ArrayList<>();
        Status status = JSONObject.parseObject(msgObj.toJSONString(), Status.class);
        // Long tagId = msgObj.getLong("tag_id");
        Long tagId = status.getTagId();
        Warning warning = null;
        Person person = personService.getPerson(tagId);
        if (person != null) {
            //缓存原始消息,一个tagId只缓存一条
            String oldStatus = statusService.redisGet(ConstantUtil.STATUS_KEY_PRE, tagId);
            if (oldStatus != null && !oldStatus.isEmpty()) {
                Integer heartRate = status.getHeart();
                //处理异常心率
                if (heartRate == null || heartRate <= 0 || heartRate >= 200) {
                    status.setHeart(ConstantUtil.STATUS_NONE);
                }
                //处理异常电量值
                Integer power = status.getPower();
                if (power == null || power <= 0 || power >= 100) {
                    status.setPower(ConstantUtil.STATUS_NONE);
                }
                String newStatus = JSON.toJSONString(status);
                if (!newStatus.equals(oldStatus)) {
                    statusService.redisAdd(ConstantUtil.STATUS_KEY_PRE, tagId, newStatus);
                }
            } else {
                statusService.redisAdd(ConstantUtil.STATUS_KEY_PRE, tagId, JSON.toJSONString(msgObj));
            }

            warning = this.handleHeart(status, tagId);
            if (warning != null) {
                statusWarnings.add(warning);
            }

            warning = this.handlePower(status, tagId);
            if (warning != null) {
                statusWarnings.add(warning);
            }

            warning = this.handleSos(status, oldStatus, tagId);
            if (warning != null) {
                statusWarnings.add(warning);
            }

            warning = this.handleWristlet(status, oldStatus, tagId);
            if (warning != null) {
                statusWarnings.add(warning);
            }
        } else {
            logger.info("=============处理状态消息时找不到个人信息===================");
            //清理无效的状态缓存，这里指未与人关联的手环的状态数据删除掉
            String statusStr = statusService.redisGet(ConstantUtil.STATUS_KEY_PRE, tagId);
            if (statusStr != null && statusStr.isEmpty()) {
                statusService.redisDel(ConstantUtil.STATUS_KEY_PRE, tagId);
            }
        }
        return statusWarnings;
    }

    private Warning handleHeart(Status status, Long tagId) {
        String heartAlarmMsg = "";
        Warning warning = null;
        Integer heartRate = status.getHeart();
        Long timestamp = status.getTimestamp();
        Integer hCount = 0;
        Integer lCount = 0;
        String key = ConstantUtil.HEART_COUNT_KEY_PRE + ":" + tagId.toString();
        hCount = hashOperations.get(key, ConstantUtil.HEART_COUNT_HIGHT);
        lCount = hashOperations.get(key, ConstantUtil.HEART_COUNT_LOW);

        if (hCount == null) {
            hCount = 0;
        }

        if (lCount == null) {
            lCount = 0;
        }

        if (heartRate.intValue() >= ConstantUtil.HEART_UP_THRESHOLD.intValue() && heartRate.intValue() != ConstantUtil.STATUS_NONE.intValue() && heartRate.intValue() != ConstantUtil.ZERO_THRESHOLD.intValue()) {
            hCount += 1;
            //更新计数器
            hashOperations.put(key, ConstantUtil.HEART_COUNT_HIGHT, hCount);
            if (hCount.intValue() >= ConstantUtil.HEART_ALARM_COUNT.intValue()) {
                heartAlarmMsg = "手环心率过高";
            }
        } else if (heartRate.intValue() <= ConstantUtil.HEART_LOW_THRESHOLD.intValue()) {
            lCount += 1;
            //更新计数器
            if (lCount.intValue() >= ConstantUtil.HEART_ALARM_COUNT.intValue() - 1) {
                heartAlarmMsg = "手环心率过低";
            }
            hashOperations.put(key, ConstantUtil.HEART_COUNT_LOW, lCount);
        }else  if (heartRate.intValue() > ConstantUtil.HEART_LOW_THRESHOLD.intValue() && heartRate.intValue() < ConstantUtil.HEART_UP_THRESHOLD.intValue()) {
            //当心率值为正常时，要取消已存在的心率报警
            String oldAlarm = statusService.redisGet(ConstantUtil.HEART_ALARM_KEY_PRE, tagId);
            if (oldAlarm != null && !oldAlarm.isEmpty()) {
                logger.info("====取消心率报警====");
                Warning oldWarning = JSONObject.parseObject(oldAlarm, Warning.class);
                oldWarning.setOp(ConstantUtil.ALARM_OFF);
                warning = oldWarning;
                //删除心率报警缓存
                statusService.redisDel(ConstantUtil.HEART_ALARM_KEY_PRE, tagId);
                hCount = 0;
                lCount = 0;
                //更新计数器
                hashOperations.put(key, ConstantUtil.HEART_COUNT_HIGHT, hCount);
                hashOperations.put(key, ConstantUtil.HEART_COUNT_LOW, lCount);
            }
        } else if (heartRate.intValue() == ConstantUtil.STATUS_NONE.intValue() || heartRate.intValue() == ConstantUtil.ZERO_THRESHOLD.intValue()) {
            logger.info("当前心率值为:" + heartRate + "获取心率异常");
        }

        if (!heartAlarmMsg.isEmpty()) {
            logger.info(heartAlarmMsg);
            warning = this.createHeartAlarm(status, heartAlarmMsg, tagId, timestamp);
        }
        return warning;
    }


    private Warning handlePower(Status status, Long tagId) {
        String powerAlarmMsg = "";
        Warning warning = null;
        Integer power = status.getPower();
        Long timestamp = status.getTimestamp();
        if (power.intValue() <= ConstantUtil.POWER_LOW_THRESHOLD.intValue() && power.intValue() != ConstantUtil.ZERO_THRESHOLD.intValue()) {
            powerAlarmMsg = "手环电量过低";
        } else if (power.intValue() > ConstantUtil.POWER_LOW_THRESHOLD.intValue() && power.intValue() <= ConstantUtil.POWER_UP_THRESHOLD.intValue()) {
            String oldAlarm = statusService.redisGet(ConstantUtil.POWER_ALARM_KEY_PRE, tagId);
            if (oldAlarm != null && !oldAlarm.isEmpty()) {
                //取消电量报警
                logger.info("=========取消电量报警======");
                Warning oldWarning = JSONObject.parseObject(oldAlarm, Warning.class);
                oldWarning.setOp(ConstantUtil.ALARM_OFF);
                warning = oldWarning;
                //删除电量报警缓存
                statusService.redisDel(ConstantUtil.POWER_ALARM_KEY_PRE, tagId);
            }
        } else if (power.intValue() == ConstantUtil.STATUS_NONE.intValue() || power.intValue() > ConstantUtil.POWER_UP_THRESHOLD.intValue() || power.intValue() == ConstantUtil.ZERO_THRESHOLD) {
            logger.info("当前电量值为：" + power.intValue() + "获取电量异常");
        }


        if (!powerAlarmMsg.isEmpty()) {
            logger.info(powerAlarmMsg);
            warning = this.createPowerAlarm(status, powerAlarmMsg, tagId, timestamp);
        }
        return warning;
    }

    //当一次按钮状态与当前按钮状态不一致就触发告警
    private Warning handleSos(Status status, String oldStatus, Long tagId) {
        Warning warning = null;
        if (oldStatus != null) {
            Status oldStatusObj = JSONObject.parseObject(oldStatus, Status.class);
            String sosAlarmMsg = "";
            Integer oldSos = oldStatusObj.getSos();
            Integer sos = status.getSos();
            Long timestamp = status.getTimestamp();
            if (sos.intValue() != ConstantUtil.STATUS_OFF.intValue() && sos.intValue() != ConstantUtil.STATUS_ON.intValue()) {
                logger.info("=======手环SOS消息异常:" + sos + "======");
            }

            if (oldSos.intValue() == ConstantUtil.STATUS_OFF.intValue()) {
                if (sos.intValue() != oldSos.intValue() && sos.intValue() != ConstantUtil.STATUS_NONE.intValue()) {
                    sosAlarmMsg = "手环SOS求救报警";
                }
            } else if (oldSos.intValue() == ConstantUtil.STATUS_ON.intValue()) {
                if (sos.intValue() != oldSos.intValue() && sos.intValue() != ConstantUtil.STATUS_NONE.intValue()) {
                    logger.info("====取消手环SOS求救报警==");
                    String oldAlarm = statusService.redisGet(ConstantUtil.SOS_ALARM_KEY_PRE, tagId);
                    //生成取消报警
                    if (oldAlarm != null && !oldAlarm.isEmpty()) {
                        Warning oldWarning = JSONObject.parseObject(oldAlarm, Warning.class);
                        oldWarning.setOp(ConstantUtil.ALARM_OFF);
                        warning = oldWarning;
                        //删除sos报警缓存
                        statusService.redisDel(ConstantUtil.SOS_ALARM_KEY_PRE, tagId);
                    }
                }
            }

            if (!sosAlarmMsg.isEmpty()) {
                logger.info(sosAlarmMsg);
                warning = this.createSosAlarm(status, sosAlarmMsg, tagId, timestamp);
            }
        }
        return warning;
    }

    private Warning handleWristlet(Status status, String oldStatus, Long tagId) {
        Warning warning = null;
        if (oldStatus != null) {
            Status oldStatusObj = JSONObject.parseObject(oldStatus, Status.class);
            String wristletAlarmMsg = "";
            Integer oldWristlet = oldStatusObj.getWristlet();
            Integer wristlet = status.getWristlet();
            Long timestamp = status.getTimestamp();

            if (wristlet.intValue() != ConstantUtil.STATUS_OFF.intValue() && wristlet.intValue() != ConstantUtil.STATUS_ON.intValue()) {
                logger.info("=======手环腕带消息异常:" + wristlet + "======");
            }
            if (oldWristlet.intValue() == ConstantUtil.STATUS_OFF.intValue()) {
                if (wristlet.intValue() != oldWristlet.intValue() && wristlet.intValue() != ConstantUtil.STATUS_NONE.intValue()) {
                    wristletAlarmMsg = "腕带拆除报警";
                }
            } else if (oldWristlet.intValue() == ConstantUtil.STATUS_ON.intValue()) {
                if (wristlet.intValue() != wristlet.intValue() && wristlet.intValue() != ConstantUtil.STATUS_NONE.intValue()) {
                    logger.info("====取消腕带拆除报警==");
                    String oldAlarm = statusService.redisGet(ConstantUtil.WRISTLET_ALARM_KEY_PRE, tagId);
                    //生成取消报警
                    if (oldAlarm != null && !oldAlarm.isEmpty()) {
                        Warning oldWarning = JSONObject.parseObject(oldAlarm, Warning.class);
                        oldWarning.setOp(ConstantUtil.ALARM_OFF);
                        warning = oldWarning;
                        //删除sos报警缓存
                        statusService.redisDel(ConstantUtil.WRISTLET_ALARM_KEY_PRE, tagId);
                    }
                }
            }

            if (!wristletAlarmMsg.isEmpty()) {
                logger.info(wristletAlarmMsg);
                warning = this.createWristletAlarm(status, wristletAlarmMsg, tagId, timestamp);
            }
        }
        return warning;
    }

    //给坐标添加报警状态
    private void setCoordinateType(Coordinate coordinate) {
        coordinate.setType(ConstantUtil.COOR_FENCE);
    }

    //设置人名，工号，部门名，部门编号，级别，职务，这些都是从缓存获取，所以是动态变化的
    private Coordinate setCoodinateAttr(Coordinate coordinate, Person person, Department department, String leveCode, String positionCode) {
        coordinate.setPersonName(person.getPersonName());
        coordinate.setPersonCode(person.getPersonCode());
        coordinate.setDepartmentName(department.getDepartmentName());
        coordinate.setDepartmentCode(department.getDepartmentCode());
        String levelName = "";
        String positionName = "";
        if (leveCode != null && !leveCode.isEmpty()) {
            String levelStr = levelService.redisGet(ConstantUtil.LEVEL_KEY_PRE, leveCode);
            if (levelStr != null && !levelStr.isEmpty()) {
                Level level = JSONObject.parseObject(levelStr, Level.class);
                levelName = level.getLevelName();
            }
        }
        coordinate.setLevelName(levelName);

        if (positionCode != null && !positionCode.isEmpty()) {
            String positionStr = positionService.redisGet(ConstantUtil.POSITION_KEY_PRE, positionCode);
            if (positionStr != null && !positionStr.isEmpty()) {
                Position position = JSONObject.parseObject(positionStr, Position.class);
                positionName = position.getPositionName();
            }
        }
        coordinate.setPositionName(positionName);
        return coordinate;
    }

    //构造围栏报警消息
    private Warning newWarning(Integer type, String warningMsg, Integer op, Coordinate coordinate) {
        Warning warning = new Warning();
        warning.setStrategyCode(coordinate.getStrategyCode());
        warning.setPersonName(coordinate.getPersonName());
        warning.setPersonCode(coordinate.getPersonCode());
        warning.setDepartmentName(coordinate.getDepartmentName());
        warning.setDepartmentCode(coordinate.getDepartmentCode());
        warning.setFenceName(coordinate.getFenceName());
        warning.setFenceCode(coordinate.getFenceCode());
        warning.setPosX(coordinate.getPosX());
        warning.setPosY(coordinate.getPosY());
        warning.setPosZ(coordinate.getPosZ());
        warning.setTagId(coordinate.getTagId());
        warning.setLevel(coordinate.getLevel());
        warning.setType(type);
        warning.setMsg(warningMsg);
        warning.setOp(op);
        warning.setTimestamp(coordinate.getTimestamp());
        return warning;
    }

    //构造状态报警消息
    private Warning newStatusWarning(Status status, Long tagId, Integer level, Integer type, String warningMsg, Integer op, Long timestamp) {
        Warning warning = null;
        Coordinate coordinate = coordinateService.getReidsCoordinate(tagId);
        if (coordinate != null) {
            warning = new Warning();
            warning.setPersonName(coordinate.getPersonName());
            warning.setPersonCode(coordinate.getPersonCode());
            warning.setDepartmentName(coordinate.getDepartmentName());
            warning.setDepartmentCode(coordinate.getDepartmentCode());
            warning.setPosX(coordinate.getPosX());
            warning.setPosY(coordinate.getPosY());
            warning.setPosZ(coordinate.getPosZ());
            warning.setTagId(coordinate.getTagId());
            warning.setLevel(level);
            warning.setType(type);
            warning.setMsg(warningMsg);
            warning.setOp(op);
            if (type.intValue() == ConstantUtil.HEART_ALARM.intValue()) {
                warning.setHeart(status.getHeart());
            } else if (type.intValue() == ConstantUtil.SOS_ALARM.intValue()) {
                warning.setSos(status.getSos());
            } else if (type.intValue() == ConstantUtil.WRISTLET_ALARM.intValue()) {
                warning.setWristlet(status.getWristlet());
            } else if (type.intValue() == ConstantUtil.POWER_ALARM.intValue()) {
                warning.setPower(status.getPower());
            }
            warning.setOp(op);
            warning.setTimestamp(timestamp);
        } else {
            //当上一次的坐标不存在时使用原点坐标表示未获取到坐标
            warning = new Warning();
            Person person = personService.getPerson(tagId);
            Department department = departmentService.getCache(person.getDepartmentCode());
            warning.setPersonName(person.getPersonName());
            warning.setPersonCode(person.getPersonCode());
            warning.setDepartmentName(department.getDepartmentName());
            warning.setDepartmentCode(department.getDepartmentCode());
            warning.setPosX(0D);
            warning.setPosY(0D);
            warning.setPosZ(0D);
            warning.setTagId(tagId);
            warning.setLevel(level);
            warning.setType(type);
            warning.setMsg(warningMsg);
            warning.setOp(op);
            if (type.intValue() == ConstantUtil.HEART_ALARM.intValue()) {
                warning.setHeart(status.getHeart());
            } else if (type.intValue() == ConstantUtil.SOS_ALARM.intValue()) {
                warning.setSos(status.getSos());
            } else if (type.intValue() == ConstantUtil.WRISTLET_ALARM.intValue()) {
                warning.setWristlet(status.getWristlet());
            } else if (type.intValue() == ConstantUtil.POWER_ALARM.intValue()) {
                warning.setPower(status.getPower());
            }
            warning.setTimestamp(timestamp);
        }
        return warning;

    }

    //生成报警消息和带状态的坐标信息,这里只是让坐标消息带上报警,并没有指定带具体哪类报警的状态
    private Warning createfenceAlarm(String warningMsg, List<String> warningLsOld, Coordinate coordinate) {
        Warning warning = null;
        // 这里只是让坐标消息带上报警,并没有指定带具体哪类报警的状态
        this.setCoordinateType(coordinate);
        Long curTagId = coordinate.getTagId();
        //判断旧报警中是否已有相同报警如果有则不添加新的报警
        Boolean isExist = false;
        if (warningLsOld != null && warningLsOld.size() > 0) {
            //判断是否之前添加过报警如果添加过则不添加新的
            for (String redisWarningStr : warningLsOld) {
                JSONObject redisWarning = JSON.parseObject(redisWarningStr);

                String curCode = coordinate.getStrategyCode();
                Long redisTagId = redisWarning.getLong("tagId");
                String redisCode = redisWarning.getString("strategyCode");
                if (curTagId.longValue() == redisTagId.longValue() && curCode.equals(redisCode)) {
                    isExist = true;
                    break;
                }
            }
            //判断是否之前添加过报警如果没有添加
            if (!isExist) {
                warning = this.createFenceAlarm(warningMsg, coordinate);
            }
        } else {
            warning = this.createFenceAlarm(warningMsg, coordinate);
        }
        return warning;
    }

    //生成围栏报警消息,并写入缓存中,报警级别由策略级别决定
    private Warning createFenceAlarm(String warningMsg, Coordinate coordinate) {
        Warning warning = this.newWarning(ConstantUtil.FENCE_ALARM, warningMsg, ConstantUtil.ALARM_ON, coordinate);
        Long tagId = coordinate.getTagId();
        warningService.addRedisWarning(tagId, JSON.toJSONString(warning));
        return warning;
    }

    //生成心率报警消息,并写入缓存中,下一次坐标消息到来会遍历此报警
    private Warning createHeartAlarm(Status status, String warningMsg, Long tagId, Long timestamp) {
        String oldWarning = statusService.redisGet(ConstantUtil.HEART_ALARM_KEY_PRE, tagId);
        Warning warning = null;
        //相同报警不重复发推送
        if (isSameWarning(warningMsg, oldWarning)) return null;
        warning = this.newStatusWarning(status, tagId, ConstantUtil.ALARM_URGEN, ConstantUtil.HEART_ALARM, warningMsg, ConstantUtil.ALARM_ON, timestamp);
        statusService.redisAdd(ConstantUtil.HEART_ALARM_KEY_PRE, tagId, JSON.toJSONString(warning));
        return warning;
    }

    private boolean isSameWarning(String warningMsg, String oldWarning) {
        Warning warning;
        if (oldWarning != null && !oldWarning.isEmpty()) {
            warning = JSONObject.parseObject(oldWarning, Warning.class);
            String oldMsg = warning.getMsg();
            if (oldMsg.equals(warningMsg)) {
                return true;
            }
        }
        return false;
    }

    //生成电量报警消息,并写入缓存中,下一次坐标消息到来会遍历此报警
    private Warning createPowerAlarm(Status status, String warningMsg, Long tagId, Long timestamp) {
        String oldWarning = statusService.redisGet(ConstantUtil.POWER_ALARM_KEY_PRE, tagId);
        Warning warning = null;
        //相同报警不重复推送
        if (isSameWarning(warningMsg, oldWarning)) return null;
        //将上一次的坐标添加到报警中表明报警产生的位置
        warning = this.newStatusWarning(status, tagId, ConstantUtil.ALARM_COMM, ConstantUtil.POWER_ALARM, warningMsg, ConstantUtil.ALARM_ON, timestamp);
        //添加报警缓存
        statusService.redisAdd(ConstantUtil.POWER_ALARM_KEY_PRE, tagId, JSON.toJSONString(warning));
        return warning;
    }

    private Warning createSosAlarm(Status status, String warningMsg, Long tagId, Long timestamp) {
        String oldWarning = statusService.redisGet(ConstantUtil.SOS_ALARM_KEY_PRE, tagId);
        Warning warning = null;
        //相同报警不重复推送
        if (isSameWarning(warningMsg, oldWarning)) return null;
        //将上一次的坐标添加到报警中表明报警产生的位置
        warning = this.newStatusWarning(status, tagId, ConstantUtil.ALARM_URGEN, ConstantUtil.SOS_ALARM, warningMsg, ConstantUtil.ALARM_ON, timestamp);
        //添加报警缓存
        statusService.redisAdd(ConstantUtil.SOS_ALARM_KEY_PRE, tagId, JSON.toJSONString(warning));
        return warning;
    }

    private Warning createWristletAlarm(Status status, String warningMsg, Long tagId, Long timestamp) {
        String oldWarning = statusService.redisGet(ConstantUtil.WRISTLET_ALARM_KEY_PRE, tagId);
        Warning warning = null;
        //相同报警不重复推送
        if (isSameWarning(warningMsg, oldWarning)) return null;
        //将上一次的坐标添加到报警中表明报警产生的位置
        warning = this.newStatusWarning(status, tagId, ConstantUtil.ALARM_URGEN, ConstantUtil.WRISTLET_ALARM, warningMsg, ConstantUtil.ALARM_ON, timestamp);
        //添加报警缓存
        statusService.redisAdd(ConstantUtil.WRISTLET_ALARM_KEY_PRE, tagId, JSON.toJSONString(warning));
        return warning;
    }

    //处理直接报警消息,生成报警消息缓存
    private Warning createWarning(String personName, JSONObject msgObj, List<Warning> warningLsOld) {
        Integer warningType = msgObj.getInteger("warning_type");
        Long tagId = msgObj.getLong("tag_id");
        Warning warning = null;
        //判断旧报警中是否已有相同报警如果有则不添加新的报警
        Boolean isExist = false;
        if (warningLsOld != null && warningLsOld.size() > 0) {
            //判断是否之前添加过报警如果添加过则不添加新的
            for (Warning redisWarning : warningLsOld) {
                Long redisTagId = redisWarning.getTagId();
                Integer redisType = redisWarning.getType();
                if (tagId.longValue() == redisTagId.longValue() && warningType.intValue() == redisType.intValue()) {
                    isExist = true;
                }
            }
            if (!isExist) {
                warning = this.createWarning(personName, msgObj);
            }
        } else {
            warning = this.createWarning(personName, msgObj);
        }
        return warning;
    }

    //处理直接报警消息,生成报警消息
    private Warning createWarning(String personName, JSONObject msgObj) {
        Warning warning = new Warning();
        Integer warningType = msgObj.getInteger("warning_type");
        Long timestamp = msgObj.getLong("timestamp");
        Long tagId = msgObj.getLong("tag_id");
        String warningMsg = "";
        if (warningType == 2) {
            warningMsg = "SOS求救报警";
            warning.setLevel(2);
        } else if (warningType == 3) {
            warningMsg = "跌倒求救报警";
            warning.setLevel(2);
        }
        warning.setPersonName(personName);
        warning.setTagId(tagId);
        warning.setType(warningType);
        warning.setMsg(warningMsg);
        warning.setTimestamp(timestamp);
        String warningStr = JSON.toJSONString(warning);
        warningService.addRedisWarning(tagId, warningStr);
        return warning;
    }

    //处理直接报警消息,拼成带坐标的报警,
    private Coordinate setCoordinate(Coordinate coordinate, JSONObject msgObj) {
        Integer warningType = msgObj.getInteger("warning_type");
        Long timestamp = msgObj.getLong("timestamp");
        String warningMsg = "";
        if (warningType == 2) {
            warningMsg = "SOS求救报警";
            coordinate.setType(3);
            coordinate.setMsg(warningMsg);
        } else if (warningType == 3) {
            warningMsg = "跌倒求救报警";
            coordinate.setMsg(warningMsg);
            coordinate.setType(3);
        }
        coordinate.setTimestamp(timestamp);
        return coordinate;
    }


    //如果旧报警存在，而当前坐标位置不报警状态，则要删除旧报警，并生成旧报警取消消息
    private Warning cancelFenceAlarm(Coordinate coordinate, List<String> warningLsOld) {
        Warning warningCancel = null;
        for (String warningStr : warningLsOld) {
            Warning warning = JSON.parseObject(warningStr, Warning.class);
            Long curTagId = coordinate.getTagId();
            String curCode = coordinate.getStrategyCode();
            Long redisTagId = warning.getTagId();
            String redisCode = warning.getStrategyCode();
            if (curTagId.longValue() == redisTagId.longValue() && curCode.equals(redisCode)) {
                // 删除旧报警缓存
                warningService.deleteRedisWarning(redisTagId, warningStr);
                //设置报警取消消息
                warning.setOp(ConstantUtil.ALARM_OFF);
                warning.setTimestamp(coordinate.getTimestamp());
                warningCancel = warning;
            }
        }
        return warningCancel;
    }

}
