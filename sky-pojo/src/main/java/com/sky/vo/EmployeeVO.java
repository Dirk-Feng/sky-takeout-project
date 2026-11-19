package com.sky.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 员工分页展示信息，不包含密码。 */
@Data
public class EmployeeVO implements Serializable {
    private Long id;
    private String username;
    private String name;
    private String phone;
    private String sex;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}
