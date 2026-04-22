package com.seedcrm.crm.salary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WithdrawRecordMapper extends BaseMapper<WithdrawRecord> {

    @Select("""
            SELECT id, user_id, amount, status, create_time
            FROM withdraw_record
            WHERE id = #{id}
            FOR UPDATE
            """)
    WithdrawRecord selectByIdForUpdate(@Param("id") Long id);
}
