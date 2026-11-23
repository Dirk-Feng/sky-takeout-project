package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.exception.BaseException;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AddressBookServiceImpl implements AddressBookService {
    private final AddressBookMapper mapper;
    public AddressBookServiceImpl(AddressBookMapper mapper) { this.mapper = mapper; }
    private Long currentUser() {
        Long id = BaseContext.getCurrentId();
        if (id == null) throw new BaseException("用户未登录");
        return id;
    }
    public List<AddressBook> list(AddressBook query) {
        List<AddressBook> addresses = mapper.list(currentUser());
        if (query != null && query.getIsDefault() != null) {
            return addresses.stream().filter(a -> query.getIsDefault().equals(a.getIsDefault())).collect(Collectors.toList());
        }
        return addresses;
    }
    public void save(AddressBook address) {
        address.setUserId(currentUser()); address.setIsDefault(0); mapper.insert(address);
    }
    public AddressBook getById(Long id) {
        AddressBook address = mapper.getById(id, currentUser());
        if (address == null) throw new BaseException("地址不存在");
        return address;
    }
    public void update(AddressBook address) {
        address.setUserId(currentUser());
        if (mapper.update(address) == 0 && mapper.getById(address.getId(), address.getUserId()) == null) throw new BaseException("地址不存在");
    }
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(AddressBook address) {
        Long userId = currentUser();
        if (mapper.getById(address.getId(), userId) == null) throw new BaseException("地址不存在");
        mapper.clearDefault(userId);
        if (mapper.setDefault(address.getId(), userId) != 1) throw new BaseException("设置默认地址失败");
    }
    public void deleteById(Long id) {
        if (mapper.deleteById(id, currentUser()) == 0) throw new BaseException("地址不存在");
    }
}
