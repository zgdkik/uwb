package com.zhilutec.dbs.entities;


import javax.persistence.Table;
import javax.persistence.Transient;

@Table(name = "uwb_warnings")
public class Warning extends Base {

    private Double posX; // decimal(12,8) NOT NULL COMMENT 'x坐标', '坐标产生时间',
    private Double posY; // decimal(12,8) NOT NULL COMMENT 'y坐标',
    private Double posZ; // decimal(12,8) NOT NULL COMMENT 'z坐标',
    private Long timestamp;
    private Integer type;//` tinyint(2) DEFAULT NULL COMMENT '消息类别',
    private Integer level;
    private String msg;
    private Long tagId;
    private String personCode;
    private String personName;
    private String strategyName;
    private String strategyCode;
    private String departmentName;
    private String departmentCode;
    private String fenceName;
    private String fenceCode;
    private Integer op;
    private Integer status;
    private String remark;

    @Transient
    private Long finishTime;


    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(Long finishTime) {
        this.finishTime = finishTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }


    public Double getPosX() {
        return posX;
    }

    public void setPosX(Double posX) {
        this.posX = posX;
    }

    public Double getPosY() {
        return posY;
    }

    public void setPosY(Double posY) {
        this.posY = posY;
    }

    public Double getPosZ() {
        return posZ;
    }

    public void setPosZ(Double posZ) {
        this.posZ = posZ;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getPersonCode() {
        return personCode;
    }

    public void setPersonCode(String personCode) {
        this.personCode = personCode;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getStrategyCode() {
        return strategyCode;
    }

    public void setStrategyCode(String strategyCode) {
        this.strategyCode = strategyCode;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public Integer getOp() {
        return op;
    }

    public void setOp(Integer op) {
        this.op = op;
    }

    public String getFenceName() {
        return fenceName;
    }

    public void setFenceName(String fenceName) {
        this.fenceName = fenceName;
    }

    public String getFenceCode() {
        return fenceCode;
    }

    public void setFenceCode(String fenceCode) {
        this.fenceCode = fenceCode;
    }
    
    @Override
    public String toString() {
        return "Warning{" +
                "posX=" + posX +
                ", posY=" + posY +
                ", posZ=" + posZ +
                ", timestamp=" + timestamp +
                ", type=" + type +
                ", level=" + level +
                ", msg='" + msg + '\'' +
                ", tagId=" + tagId +
                ", personCode='" + personCode + '\'' +
                ", personName='" + personName + '\'' +
                ", strategyName='" + strategyName + '\'' +
                ", strategyCode='" + strategyCode + '\'' +
                ", departmentName='" + departmentName + '\'' +
                ", departmentCode='" + departmentCode + '\'' +
                ", fenceName='" + fenceName + '\'' +
                ", fenceCode='" + fenceCode + '\'' +
                ", op=" + op +
                ", status=" + status +
                ", remark='" + remark + '\'' +
                ", finishTime=" + finishTime +
                '}';
    }

}
