package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

@Data
/** 修改密码请求：仅包含旧密码和新密码。 */
public class EmployeeEditPasswordDTO implements Serializable {
    private String oldPassword;
    private String newPassword;
}
