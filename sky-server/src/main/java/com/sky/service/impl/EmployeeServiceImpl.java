package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.EmployeeVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.util.List;
import java.util.stream.Collectors;
import com.sky.dto.EmployeeDTO;
import com.sky.constant.PasswordConstant;
import com.sky.context.BaseContext;
import com.sky.exception.BaseException;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    public PageResult<EmployeeVO> pageQuery(EmployeePageQueryDTO query) {
        if (query == null || query.getPage() < 1 || query.getPageSize() < 1 || query.getPageSize() > 10) {
            throw new BaseException("页码须大于零，每页数量须为1至10");
        }
        PageHelper.startPage(query.getPage(), query.getPageSize());
        // mapper被拦截, 右边其实是Page
        List<Employee> employees = employeeMapper.pageQuery(query);
        // 用PageInfo能顺带识别传入的employees是否是Page对象
        PageInfo<Employee> pageInfo = new PageInfo<>(employees);
        List<EmployeeVO> records = pageInfo.getList().stream().map(employee -> {
            EmployeeVO vo = new EmployeeVO();
            BeanUtils.copyProperties(employee, vo);
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(pageInfo.getTotal(), records);
    }

    /**
     * 员工登录
     *
     * @param employeeLoginDTO 登录信息
     * @return 员工信息
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        // 根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        // 处理用户名不存在的情况
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 将传入密码进行 MD5 加密，再与数据库中的密文比较
        String encryptedPassword = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!encryptedPassword.equals(employee.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        // 检查账号是否被锁定
        if (employee.getStatus() == StatusConstant.DISABLE) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        // 返回员工实体对象
        return employee;
    }

    /*
    * 新增员工
    * 对象属性cpy
    * 默认正常状态
    * 调用mapper.insert
    * */
    public void save(EmployeeDTO dto) {
        if (dto == null || dto.getUsername() == null || !dto.getUsername().matches("[a-zA-Z0-9]{3,20}")) throw new BaseException("Invalid username");
        if (dto.getName() == null || !dto.getName().matches("[\u4e00-\u9fffA-Za-z]{1,12}")) throw new BaseException("Invalid name");
        if (dto.getPhone() == null || !dto.getPhone().matches("1[3-9][0-9]{9}")) throw new BaseException("Invalid phone");
        if (!"0".equals(dto.getSex()) && !"1".equals(dto.getSex())) throw new BaseException("Invalid sex");
        if (!isValidIdNumberFormat(dto.getIdNumber())) throw new BaseException("Invalid identity number");
        if (employeeMapper.getByUsername(dto.getUsername()) != null) throw new BaseException("Username already exists");
        Long id = BaseContext.getCurrentId();
        if (id == null) throw new BaseException("Missing authenticated user");
        LocalDateTime now = LocalDateTime.now();
        Employee employee = Employee.builder().username(dto.getUsername()).name(dto.getName()).phone(dto.getPhone())
                .sex(dto.getSex()).idNumber(dto.getIdNumber()).password(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()))
                .status(StatusConstant.ENABLE).createTime(now).updateTime(now).createUser(id).updateUser(id).build();
        try { employeeMapper.insert(employee); }
        catch (DuplicateKeyException ex) {
            if (employeeMapper.getByUsername(dto.getUsername()) != null) throw new BaseException("Username already exists");
            throw ex;
        }
    }
    // TODO: 后续根据实际业务要求补充身份证出生日期、地区代码及校验码验证。
    private boolean isValidIdNumberFormat(String idNumber) {
        return idNumber != null && idNumber.matches("[0-9]{17}[0-9Xx]");
    }
}
