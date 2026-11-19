package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeeEditPasswordDTO;
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

    public void editPassword(EmployeeEditPasswordDTO dto) {
        if (dto == null || dto.getOldPassword() == null || dto.getOldPassword().isEmpty()
                || dto.getNewPassword() == null) throw new BaseException("密码参数不完整");
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null) throw new BaseException("未获取到当前登录员工");
        if (!dto.getNewPassword().matches("[a-zA-Z0-9]{6,20}")) throw new BaseException("新密码格式不正确");
        Employee employee = employeeMapper.getPasswordById(currentId);
        if (employee == null) throw new BaseException("员工不存在");
        String oldHash = DigestUtils.md5DigestAsHex(dto.getOldPassword().getBytes());
        if (!oldHash.equals(employee.getPassword())) throw new BaseException("旧密码错误");
        String newHash = DigestUtils.md5DigestAsHex(dto.getNewPassword().getBytes());
        // 更新时再次匹配旧摘要，避免并发修改覆盖已变更的密码。
        if (employeeMapper.updatePassword(currentId, newHash, oldHash, LocalDateTime.now()) != 1) {
            throw new BaseException("密码未更新，请重试");
        }
    }

    public void update(EmployeeDTO dto) {
        validateEmployee(dto);
        if (dto.getId() == null || dto.getId() <= 0) throw new BaseException("员工ID无效");
        Long operatorId = BaseContext.getCurrentId();
        if (operatorId == null) throw new BaseException("Missing authenticated user");
        Employee existing = employeeMapper.getByUsername(dto.getUsername());
        if (existing != null && !dto.getId().equals(existing.getId())) throw new BaseException("Username already exists");
        // 只设置表单字段和审计字段，不改变密码或账号状态。
        Employee employee = Employee.builder().id(dto.getId()).username(dto.getUsername()).name(dto.getName())
                .phone(dto.getPhone()).sex(dto.getSex()).idNumber(dto.getIdNumber())
                .updateTime(LocalDateTime.now()).updateUser(operatorId).build();
        try {
            if (employeeMapper.update(employee) == 0) throw new BaseException("员工信息未更新，请检查后重试");
        } catch (DuplicateKeyException ex) {
            Employee duplicate = employeeMapper.getByUsername(dto.getUsername());
            if (duplicate != null && !dto.getId().equals(duplicate.getId())) throw new BaseException("Username already exists");
            throw ex;
        }
    }

    public EmployeeVO getById(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException("员工ID无效");
        }
        Employee employee = employeeMapper.getById(id);
        if (employee == null) {
            throw new BaseException("员工不存在");
        }
        EmployeeVO vo = new EmployeeVO();
        BeanUtils.copyProperties(employee, vo);
        return vo;
    }

    public void startOrStop(Integer status, Long id) {
        if (status == null || (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status))) {
            throw new BaseException("员工状态无效");
        }
        if (id == null || id <= 0) {
            throw new BaseException("员工ID无效");
        }
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null) {
            throw new BaseException("未获取到当前登录员工");
        }
        Employee employee = Employee.builder().id(id).status(status)
                .updateTime(LocalDateTime.now()).updateUser(currentId).build();
        // 影响行数为零也可能与数据库更新计数方式有关，不能直接断定员工不存在。
        if (employeeMapper.update(employee) == 0) {
            throw new BaseException("员工状态未更新，请检查员工信息后重试");
        }
    }

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
        validateEmployee(dto);
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

    private void validateEmployee(EmployeeDTO dto) {
        if (dto == null || dto.getUsername() == null || !dto.getUsername().matches("[a-zA-Z0-9]{3,20}")) throw new BaseException("Invalid username");
        if (dto.getName() == null || !dto.getName().matches("[\u4e00-\u9fffA-Za-z]{1,12}")) throw new BaseException("Invalid name");
        if (dto.getPhone() == null || !dto.getPhone().matches("1[3-9][0-9]{9}")) throw new BaseException("Invalid phone");
        if (!"0".equals(dto.getSex()) && !"1".equals(dto.getSex())) throw new BaseException("Invalid sex");
        if (!isValidIdNumberFormat(dto.getIdNumber())) throw new BaseException("Invalid identity number");
    }
    // TODO: 后续根据实际业务要求补充身份证出生日期、地区代码及校验码验证。
    private boolean isValidIdNumberFormat(String idNumber) {
        return idNumber != null && idNumber.matches("[0-9]{17}[0-9Xx]");
    }
}
