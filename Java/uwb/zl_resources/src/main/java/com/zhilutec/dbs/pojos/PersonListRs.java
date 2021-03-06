package com.zhilutec.dbs.pojos;

import javax.persistence.Transient;
import java.util.Date;

public class PersonListRs {
    private  Long id;
    private String personName;
    private String personCode;  //varchar(32) NOT NULL COMMENT '编号',
    private String idcard;//` varchar(20) DEFAULT NULL COMMENT '身份证号码',
    private Integer sex; // tinyint(1) NOT NULL DEFAULT '1' COMMENT '性别 1 男 0女',
    private String remark; //varchar(255) DEFAULT NULL COMMENT '描述',
    private Long createdAt;// int(12) DEFAULT NULL COMMENT '添加时间',
    private String nativePlace;//` varchar(255) DEFAULT NULL COMMENT '籍贯',
    private Integer isdel; //` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1存在  0 不存在',
    private String telephone;//` int(12) DEFAULT NULL COMMENT '手机号码',
    private String email; // varchar(32) DEFAULT NULL COMMENT '邮箱',
    private String portrait;// varchar(32) DEFAULT NULL COMMENT '头像url',
    private String departmentCode;
    private String tagId; //将tagId以String类型来获取,解决tagId为空返回值没有此字段的问题
    private String levelCode;
    private String positionCode;
    private String departmentName;
    private String levelName;
    private String positionName;
    private Integer age;
    private Date birth;
    public Date getBirth() {
        return birth;
    }

    public void setBirth(Date birth) {
        this.birth = birth;
    }



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getPersonCode() {
        return personCode;
    }

    public void setPersonCode(String personCode) {
        this.personCode = personCode;
    }

    public String getIdcard() {
        return idcard;
    }

    public void setIdcard(String idcard) {
        this.idcard = idcard;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public String getNativePlace() {
        return nativePlace;
    }

    public void setNativePlace(String nativePlace) {
        this.nativePlace = nativePlace;
    }

    public Integer getIsdel() {
        return isdel;
    }

    public void setIsdel(Integer isdel) {
        this.isdel = isdel;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPortrait() {
        return portrait;
    }

    public void setPortrait(String portrait) {
        this.portrait = portrait;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public String getLevelCode() {
        return levelCode;
    }

    public void setLevelCode(String levelCode) {
        this.levelCode = levelCode;
    }

    public String getPositionCode() {
        return positionCode;
    }

    public void setPositionCode(String positionCode) {
        this.positionCode = positionCode;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getLevelName() {
        return levelName;
    }

    public void setLevelName(String levelName) {
        this.levelName = levelName;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
