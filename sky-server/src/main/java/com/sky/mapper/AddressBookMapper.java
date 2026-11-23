package com.sky.mapper;

import com.sky.entity.AddressBook;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AddressBookMapper {
    @Select("select * from address_book where user_id = #{userId} order by id desc")
    List<AddressBook> list(Long userId);
    @Select("select * from address_book where id = #{id} and user_id = #{userId}")
    AddressBook getById(@Param("id") Long id, @Param("userId") Long userId);
    @Insert("insert into address_book (user_id, consignee, phone, sex, province_code, province_name, city_code, city_name, district_code, district_name, detail, label, is_default) values (#{userId}, #{consignee}, #{phone}, #{sex}, #{provinceCode}, #{provinceName}, #{cityCode}, #{cityName}, #{districtCode}, #{districtName}, #{detail}, #{label}, 0)")
    void insert(AddressBook address);
    int update(AddressBook address);
    @Update("update address_book set is_default = 0 where user_id = #{userId}")
    int clearDefault(Long userId);
    @Update("update address_book set is_default = 1 where id = #{id} and user_id = #{userId}")
    int setDefault(@Param("id") Long id, @Param("userId") Long userId);
    @Delete("delete from address_book where id = #{id} and user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);
}
