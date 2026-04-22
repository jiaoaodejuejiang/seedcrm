package com.seedcrm.crm.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.finance.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    @Select("""
            SELECT id, owner_type, owner_id, create_time
            FROM account
            WHERE owner_type = #{ownerType}
              AND owner_id = #{ownerId}
            LIMIT 1
            """)
    Account selectByOwner(@Param("ownerType") String ownerType, @Param("ownerId") Long ownerId);

    @Select("""
            SELECT id, owner_type, owner_id, create_time
            FROM account
            WHERE id = #{id}
            FOR UPDATE
            """)
    Account selectByIdForUpdate(@Param("id") Long id);
}
