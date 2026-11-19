package com.sky.mapper;

import com.sky.entity.Employee;
import com.sky.dto.EmployeePageQueryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface EmployeeMapper {

    // 后续分页查询需紧跟 PageHelper.startPage 调用，插件才会拦截该次 MyBatis 查询并分页。

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    @Select("select id, username, name, phone, sex, id_number, status, create_time, update_time, create_user, update_user from employee where id = #{id}")
    Employee getById(Long id);

    @Select("select id, password from employee where id = #{id}")
    Employee getPasswordById(Long id);

    @Update("update employee set password = #{password}, update_time = #{updateTime}, update_user = #{id} where id = #{id} and password = #{oldPassword}")
    int updatePassword(@Param("id") Long id, @Param("password") String password,
                       @Param("oldPassword") String oldPassword, @Param("updateTime") LocalDateTime updateTime);

    @Insert("insert into employee (username, name, password, phone, sex, id_number, status, create_time, update_time, create_user, update_user) values (#{username}, #{name}, #{password}, #{phone}, #{sex}, #{idNumber}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    void insert(Employee employee);

    List<Employee> pageQuery(EmployeePageQueryDTO query);

    int update(Employee employee);
}
