package com.sky.mapper;

import com.sky.entity.User;
import com.sky.vo.DailyUserCountVO;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    List<DailyUserCountVO> countDailyUsers(@Param("beginTime") LocalDateTime beginTime, @Param("endTimeExclusive") LocalDateTime endTimeExclusive);
    long countUsersBefore(@Param("beginTime") LocalDateTime beginTime);
    @Select("select id, openid from user where id = #{id}")
    User getPaymentUser(Long id);
    @Select("select id, openid, name, phone, sex, id_number, avatar, create_time from user where openid = #{openid}")
    User getByOpenid(String openid);

    @Insert("insert into user (openid, create_time) values (#{openid}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);
}
